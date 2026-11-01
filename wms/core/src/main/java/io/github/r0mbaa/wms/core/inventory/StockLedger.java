package io.github.r0mbaa.wms.core.inventory;

import io.github.r0mbaa.wms.core.admin.CurrentUser;
import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.StoragePolicy;
import io.github.r0mbaa.wms.core.topology.Zone;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Единственная точка изменения остатков. Каждое изменение — это движение в журнале и
 * согласованное изменение строк остатка в одной транзакции (FR-M4-03, NFR-R-01), поэтому
 * остаток всегда восстановим из журнала (INV-04).
 *
 * <p>Порядок захвата блокировок фиксирован: сначала строка ячейки-приёмника (проверка
 * вместимости), затем строка зоны (INV-08), затем строки остатка по возрастанию id места. Так
 * встречные перемещения не блокируют друг друга взаимно.
 */
@Service
public class StockLedger {

    /** Типы, при которых товар физически кладут в ячейку и она должна его принять. */
    private static final Set<MovementType> PLACING = EnumSet.of(
            MovementType.RECEIPT, MovementType.PUTAWAY, MovementType.TRANSFER, MovementType.RETURN);

    private static final double EPS = 1e-9;

    private final StockRepository stocks;
    private final MovementRepository movements;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final Clock clock;

    StockLedger(StockRepository stocks, MovementRepository movements, JdbcTemplate jdbc, CurrentUser currentUser,
            Clock clock) {
        this.stocks = stocks;
        this.movements = movements;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Проводит движение: списывает количество из свободной части остатка источника, добавляет
     * приёмнику и пишет запись журнала.
     *
     * @throws ConflictException если товара не хватает или приёмник его не принимает
     */
    @Transactional
    public Movement post(Posting posting) {
        MovementType type = posting.type();
        Location from = posting.from();
        Location to = posting.to();
        Sku sku = posting.sku();
        type.requireDirection(from != null, to != null);
        if (posting.quantity() <= 0) {
            throw new IllegalArgumentException("Количество должно быть положительным, задано " + posting.quantity());
        }
        if (from != null && to != null) {
            if (from.getId().equals(to.getId())) {
                throw new IllegalArgumentException("Место-источник и место-приёмник совпадают: " + from.getCode());
            }
            if (!from.getWarehouse().getId().equals(to.getWarehouse().getId())) {
                throw new IllegalArgumentException("Перемещение между складами не поддерживается: "
                        + from.getCode() + " → " + to.getCode());
            }
        }
        if (from != null && type == MovementType.PICK && from.isBlocked()) {
            throw new ConflictException("Ячейка " + from.getCode() + " заблокирована (" + from.getBlockReason()
                    + "): отбор из неё невозможен, сообщите диспетчеру");
        }
        if (to != null && PLACING.contains(type)) {
            requireAccepts(to, sku, posting.quantity());
        }

        Instant now = clock.instant();
        List<Stock> rows = lockRows(sku, from, to);
        if (from != null) {
            row(rows, from).orElseThrow(() -> new ConflictException("В месте " + from.getCode() + " нет товара "
                    + sku.getArticle() + ": проверьте место или отсканируйте его заново"))
                    .remove(posting.quantity(), now);
        }
        if (to != null) {
            row(rows, to).orElseThrow().add(posting.quantity(), now);
            if (PLACING.contains(type)) {
                requireDedicatedSlot(to, sku);
            }
        }
        return movements.save(new Movement(type, sku, posting.quantity(), from, to, posting.document(),
                currentUser.username(), now, posting.comment()));
    }

    /**
     * Отбор по резерву (FR-M10-11): отобранное переходит из ячейки в тару движением {@code PICK},
     * а резерв строки снимается целиком. Если отобрано меньше резерва (недостача, повреждение,
     * недоступная ячейка), неотобранное остаётся в ячейке свободным.
     *
     * @param picked   отобрано; 0 — ничего, тогда движения нет, только снимается резерв
     * @param reserved резерв, который закрывает этот отбор
     * @return движение отбора или пусто, если ничего не отобрано
     */
    @Transactional
    public Optional<Movement> pick(Location from, Location container, Sku sku, int picked, int reserved,
            DocumentRef document, String comment) {
        if (picked < 0 || picked > reserved) {
            throw new IllegalArgumentException("Отобрано " + picked + ", а по заданию требуется не больше " + reserved);
        }
        if (picked > 0 && from.isBlocked()) {
            throw new ConflictException("Ячейка " + from.getCode() + " заблокирована (" + from.getBlockReason()
                    + "): отбор из неё невозможен, зарегистрируйте исключение «ячейка недоступна»");
        }
        Instant now = clock.instant();
        List<Stock> rows = lockRows(sku, from, picked > 0 ? container : null);
        row(rows, from).orElseThrow(() -> new IllegalStateException("Нет строки остатка под резерв в " + from.getCode()))
                .consumeReserved(picked, reserved, now);
        if (picked == 0) {
            return Optional.empty();
        }
        row(rows, container).orElseThrow().add(picked, now);
        return Optional.of(movements.save(new Movement(MovementType.PICK, sku, picked, from, container, document,
                currentUser.username(), now, comment)));
    }

    /**
     * Приводит учётный остаток к фактическому по результату пересчёта одним движением
     * {@code ADJUSTMENT} (§15.4: ручная корректировка с актом).
     *
     * @return движение корректировки
     * @throws ConflictException если факт совпадает с учётом или меньше зарезервированного
     */
    @Transactional
    public Movement adjustTo(Location location, Sku sku, int actualQuantity, DocumentRef document, String reason) {
        if (actualQuantity < 0) {
            throw new IllegalArgumentException("Фактическое количество не может быть отрицательным");
        }
        Stock stock = row(lockRows(sku, null, location), location).orElseThrow();
        int delta = actualQuantity - stock.getQuantity();
        if (delta == 0) {
            throw new ConflictException("Фактическое количество совпадает с учётным (" + actualQuantity
                    + "): корректировка не нужна");
        }
        if (actualQuantity < stock.getReservedQuantity()) {
            throw new ConflictException("В месте " + location.getCode() + " под заказы зарезервировано "
                    + stock.getReservedQuantity() + " " + sku.getUom() + ", а по факту " + actualQuantity
                    + ": сначала снимите резерв или переаллоцируйте заказы");
        }
        Posting posting = delta > 0
                ? new Posting(MovementType.ADJUSTMENT, sku, delta, null, location, document, reason)
                : new Posting(MovementType.ADJUSTMENT, sku, -delta, location, null, document, reason);
        return post(posting);
    }

    /**
     * Проверки ячейки-приёмника: активна, не заблокирована (FR-M1-11), принимает класс хранения
     * товара (FR-M1-06), выдержит вес и вместит объём, а в зоне с выделенным местом товар не
     * лежит в другой ячейке (INV-08).
     */
    private void requireAccepts(Location to, Sku sku, int quantity) {
        if (!to.isCell()) {
            return;
        }
        if (!to.isActive()) {
            throw new ConflictException("Ячейки " + to.getCode() + " больше нет в планировке: выберите другую ячейку");
        }
        if (to.isBlocked()) {
            throw new ConflictException("Ячейка " + to.getCode() + " заблокирована (" + to.getBlockReason()
                    + "): выберите другую ячейку");
        }
        if (!to.accepts(sku.getStorageClass())) {
            throw new ConflictException("Ячейка " + to.getCode() + " не принимает товар класса " + sku.getStorageClass()
                    + " (допустимы: " + to.getAllowedStorageClasses().stream().sorted().toList()
                    + "): выберите другую ячейку");
        }

        // Строка ячейки блокируется, чтобы два одновременных размещения разных товаров не
        // превысили вместимость вдвоём. NO KEY UPDATE не мешает вставкам движений со ссылкой на неё.
        jdbc.queryForObject("select id from location where id = ? for no key update", Long.class, to.getId());
        Load load = jdbc.queryForObject("""
                select coalesce(sum(s.quantity * k.weight_kg), 0), coalesce(sum(s.quantity * k.volume_m3), 0)
                from stock s join sku k on k.id = s.sku_id
                where s.location_id = ?
                """, (rs, n) -> new Load(rs.getDouble(1), rs.getDouble(2)), to.getId());
        Objects.requireNonNull(load);
        double weight = quantity * sku.getWeightKg();
        if (to.getMaxWeightKg() != null && load.weightKg() + weight > to.getMaxWeightKg() + EPS) {
            throw new ConflictException(String.format(Locale.ROOT,
                    "Ячейка %s выдержит ещё %.1f кг, а размещается %.1f кг: разделите партию или выберите другую ячейку",
                    to.getCode(), Math.max(0, to.getMaxWeightKg() - load.weightKg()), weight));
        }
        double volume = quantity * sku.getVolumeM3();
        if (to.getMaxVolumeM3() != null && load.volumeM3() + volume > to.getMaxVolumeM3() + EPS) {
            throw new ConflictException(String.format(Locale.ROOT,
                    "В ячейку %s поместится ещё %.4f м³, а размещается %.4f м³: разделите партию или выберите другую ячейку",
                    to.getCode(), Math.max(0, to.getMaxVolumeM3() - load.volumeM3()), volume));
        }

        // Зона блокируется до строк остатка, сама проверка INV-08 — после проводки, см. ниже.
        if (isDedicated(to)) {
            jdbc.queryForObject("select id from zone where id = ? for no key update", Long.class,
                    to.getZone().getId());
        }
    }

    /**
     * INV-08 проверяется по состоянию уже после проводки: перенос всего товара из одной ячейки
     * зоны в другую допустим, хотя до списания товар лежал бы в двух ячейках.
     */
    private void requireDedicatedSlot(Location to, Sku sku) {
        if (!to.isCell() || !isDedicated(to)) {
            return;
        }
        stocks.flush();
        Zone zone = to.getZone();
        List<String> occupied = jdbc.queryForList("""
                select l.code from stock s join location l on l.id = s.location_id
                where l.zone_id = ? and s.sku_id = ? and s.quantity > 0 and l.id <> ?
                limit 1
                """, String.class, zone.getId(), sku.getId(), to.getId());
        if (!occupied.isEmpty()) {
            throw new ConflictException("Зона " + zone.getCode() + " работает с выделенным местом, а товар "
                    + sku.getArticle() + " уже лежит в ячейке " + occupied.getFirst()
                    + ": разместите его туда, а излишки — в зону навала (INV-08)");
        }
    }

    private static boolean isDedicated(Location location) {
        Zone zone = location.getZone();
        return zone != null && zone.getStoragePolicy() == StoragePolicy.DEDICATED;
    }

    /** Гарантирует строку остатка приёмника и блокирует обе строки в порядке id места. */
    private List<Stock> lockRows(Sku sku, Location from, Location to) {
        List<Long> ids = new ArrayList<>(2);
        if (from != null) {
            ids.add(from.getId());
        }
        if (to != null) {
            jdbc.update("""
                    insert into stock (location_id, sku_id, quantity, reserved_quantity, updated_at)
                    values (?, ?, 0, 0, now())
                    on conflict (location_id, sku_id) do nothing
                    """, to.getId(), sku.getId());
            ids.add(to.getId());
        }
        return stocks.lockForUpdate(sku, ids);
    }

    private static Optional<Stock> row(List<Stock> rows, Location location) {
        return rows.stream().filter(s -> s.getLocation().getId().equals(location.getId())).findFirst();
    }

    private record Load(double weightKg, double volumeM3) {
    }

    /**
     * Проводка одного движения.
     *
     * @param from источник; {@code null} для прихода и корректировки в плюс
     * @param to   приёмник; {@code null} для отгрузки, списания и корректировки в минус
     */
    public record Posting(MovementType type, Sku sku, int quantity, Location from, Location to,
            DocumentRef document, String comment) {

        public Posting {
            Objects.requireNonNull(type);
            Objects.requireNonNull(sku);
            Objects.requireNonNull(document);
        }
    }
}
