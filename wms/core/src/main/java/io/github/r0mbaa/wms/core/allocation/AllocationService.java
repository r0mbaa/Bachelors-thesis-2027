package io.github.r0mbaa.wms.core.allocation;

import io.github.r0mbaa.wms.core.allocation.AllocationRule.Candidate;
import io.github.r0mbaa.wms.core.allocation.AllocationRule.Take;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.inventory.Stock;
import io.github.r0mbaa.wms.core.inventory.StockRepository;
import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.order.OrderCancelled;
import io.github.r0mbaa.wms.core.order.OrderLine;
import io.github.r0mbaa.wms.core.order.OrderRepository;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.order.Wave;
import io.github.r0mbaa.wms.core.order.WaveService;
import io.github.r0mbaa.wms.core.order.WaveStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Резервирование остатков под заказы (M6). Состояние резервов принадлежит {@code core}, выбор
 * ячеек — правилу {@link AllocationRule} (§12.2).
 *
 * <p>Два механизма конкурентного доступа, как в §12.2:
 * <ul>
 *   <li>одиночный заказ (в том числе срочный) — оптимистическая блокировка по версии строк
 *       остатка; при конфликте аллокация повторяется целиком, пользователь ошибки не видит
 *       (FR-M6-05, NFR-R-02);</li>
 *   <li>волна — пессимистическая блокировка всех строк-кандидатов сразу, в едином порядке по
 *       id места и SKU, чтобы параллельные волны не заблокировали друг друга взаимно.</li>
 * </ul>
 */
@Service
public class AllocationService {

    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);

    private static final Set<OrderStatus> ALLOCATABLE = CustomerOrder.from(OrderStatus.NEW, OrderStatus.ALLOCATED);

    private final OrderService orderService;
    private final OrderRepository orders;
    private final WaveService waves;
    private final StockRepository stocks;
    private final AllocationRepository allocations;
    private final AllocationRule rule;
    private final AllocationProperties properties;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    AllocationService(OrderService orderService, OrderRepository orders, WaveService waves, StockRepository stocks,
            AllocationRepository allocations, AllocationRule rule, AllocationProperties properties,
            TransactionTemplate tx, JdbcTemplate jdbc, Clock clock) {
        this.orderService = orderService;
        this.orders = orders;
        this.waves = waves;
        this.stocks = stocks;
        this.allocations = allocations;
        this.rule = rule;
        this.properties = properties;
        this.tx = tx;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Резервирует незарезервированную часть строк заказа. Что не хватило, остаётся
     * необеспеченным и видно в результате (FR-M5-04); повторный вызов дорезервирует, когда товар
     * поступит.
     */
    public AllocationResult allocateOrder(String number) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> {
                    CustomerOrder order = orderService.get(number);
                    requireAllocatable(order);
                    allocate(order, stocks.allocationCandidates(order.getWarehouse().getId(), skuIds(order)));
                    return AllocationResult.of(order);
                });
            } catch (ConcurrencyFailureException e) {
                if (attempt >= properties.maxAttempts()) {
                    throw new ConflictException("Не удалось зарезервировать остатки под заказ " + number
                            + ": те же ячейки одновременно резервировали другие заказы. Повторите резервирование");
                }
                log.debug("Конфликт версий при аллокации заказа {}, попытка {}", number, attempt);
            }
        }
    }

    /**
     * Резервирует заказы волны в её порядке: ближайший дедлайн и больший приоритет получают
     * остаток первыми.
     */
    @Transactional
    public WaveAllocationResult allocateWave(String number) {
        Wave wave = waves.get(number);
        if (wave.getStatus() != WaveStatus.FORMED && wave.getStatus() != WaveStatus.ALLOCATED) {
            throw new ConflictException("Волна " + number + " в статусе " + wave.getStatus() + ": резервировать нечего");
        }
        List<CustomerOrder> waveOrders = waves.ordersOf(wave).stream()
                .filter(o -> ALLOCATABLE.contains(o.getStatus()))
                .toList();
        Set<Long> skuIds = waveOrders.stream().flatMap(o -> skuIds(o).stream()).collect(Collectors.toSet());
        List<Stock> candidates = skuIds.isEmpty()
                ? List.of()
                : stocks.lockAllocationCandidates(wave.getWarehouse().getId(), skuIds);
        waveOrders.forEach(order -> allocate(order, candidates));
        wave.setStatus(WaveStatus.ALLOCATED);
        return new WaveAllocationResult(wave.getNumber(), wave.getStatus(),
                waveOrders.stream().map(AllocationResult::of).toList());
    }

    /** Ручное снятие резервов (FR-M6-04): заказ возвращается в {@code NEW}. */
    @Transactional
    public AllocationResult releaseOrder(String number) {
        CustomerOrder order = orderService.get(number);
        order.transition(OrderStatus.NEW, CustomerOrder.from(OrderStatus.ALLOCATED));
        release(allocations.findActive(order));
        return AllocationResult.of(order);
    }

    /** Отмена заказа снимает все его резервы в той же транзакции (FR-M5-07). */
    @EventListener
    void onOrderCancelled(OrderCancelled event) {
        CustomerOrder order = orders.findById(event.orderId()).orElseThrow();
        release(allocations.findActive(order));
    }

    /**
     * Снимает резервы с истёкшим сроком (FR-M6-04) у заказов, ещё не вошедших в задание. Каждый
     * заказ обрабатывается в своей транзакции: конфликт на одном не мешает остальным.
     */
    @Scheduled(fixedDelayString = "${wms.allocation.expiry-check:PT1M}")
    public void releaseExpired() {
        Instant now = clock.instant();
        for (Long orderId : allocations.ordersWithExpired(now)) {
            try {
                tx.executeWithoutResult(status -> releaseExpiredOf(orderId, now));
            } catch (ConcurrencyFailureException e) {
                log.info("Резервы заказа {} не сняты по сроку из-за параллельного изменения, повтор при следующем проходе",
                        orderId);
            }
        }
    }

    /** Заказ вошёл в задание: его резервы больше не истекают. */
    @Transactional
    public void pin(CustomerOrder order) {
        allocations.findActive(order).forEach(a -> a.expireAt(null));
    }

    /** Задание расформировано: срок жизни резервов заказа отсчитывается заново (FR-M6-04). */
    @Transactional
    public void unpin(CustomerOrder order) {
        Instant expiresAt = clock.instant().plus(properties.reservationTtl());
        allocations.findActive(order).forEach(a -> a.expireAt(expiresAt));
    }

    /** Активные резервы заказа, из которых строятся шаги задания. */
    @Transactional(readOnly = true)
    public List<Allocation> activeOf(CustomerOrder order) {
        return allocations.findActive(order);
    }

    @Transactional(readOnly = true)
    public List<Allocation> allocationsOf(String number) {
        return allocations.findByOrder(orderService.get(number));
    }

    /**
     * Проверка INV-03: сумма активных резервов по паре «место × SKU» равна резерву в таблице
     * остатков. Пустой список — резервы целы.
     */
    @Transactional(readOnly = true)
    public List<ReservationDiscrepancy> checkReservations() {
        return jdbc.query("""
                with active as (
                    select location_id, sku_id, sum(quantity) as quantity
                    from allocation where status = 'RESERVED'
                    group by location_id, sku_id
                )
                select l.code, k.article, coalesce(s.reserved_quantity, 0), coalesce(a.quantity, 0)
                from stock s
                full join active a on a.location_id = s.location_id and a.sku_id = s.sku_id
                join location l on l.id = coalesce(s.location_id, a.location_id)
                join sku k on k.id = coalesce(s.sku_id, a.sku_id)
                where coalesce(s.reserved_quantity, 0) <> coalesce(a.quantity, 0)
                order by l.code, k.article
                """, (rs, n) -> new ReservationDiscrepancy(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getLong(4)));
    }

    private void allocate(CustomerOrder order, List<Stock> candidates) {
        Instant now = clock.instant();
        boolean reserved = false;
        for (OrderLine line : order.getLines()) {
            int need = line.unallocated();
            if (need == 0) {
                continue;
            }
            Map<Long, Stock> sources = candidates.stream()
                    .filter(s -> s.getSku().getId().equals(line.getSku().getId()) && s.available() > 0)
                    .collect(Collectors.toMap(s -> s.getLocation().getId(), Function.identity()));
            List<Take> takes = rule.choose(need, sources.values().stream()
                    .map(s -> new Candidate(s.getLocation().getId(), s.getLocation().getCode(), s.available()))
                    .toList());
            requireFeasible(takes, need, sources);
            for (Take take : takes) {
                Stock stock = sources.get(take.locationId());
                stock.reserve(take.quantity(), now);
                allocations.save(new Allocation(line, stock.getLocation(), line.getSku(), take.quantity(), now,
                        now.plus(properties.reservationTtl())));
                line.allocate(take.quantity());
                reserved = true;
            }
        }
        if (reserved) {
            order.transition(OrderStatus.ALLOCATED, ALLOCATABLE);
        }
    }

    /** Правило подключаемое, поэтому его ответ проверяется, а не принимается на веру. */
    private void requireFeasible(List<Take> takes, int need, Map<Long, Stock> sources) {
        int total = 0;
        for (Take take : takes) {
            Stock stock = sources.get(take.locationId());
            if (stock == null || take.quantity() <= 0 || take.quantity() > stock.available()) {
                throw new IllegalStateException("Правило аллокации " + rule.name() + " выбрало недоступный источник "
                        + take);
            }
            total += take.quantity();
        }
        if (total > need) {
            throw new IllegalStateException("Правило аллокации " + rule.name() + " зарезервировало " + total
                    + " при потребности " + need);
        }
    }

    private void release(List<Allocation> active) {
        if (active.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        Set<Long> locationIds = active.stream().map(a -> a.getLocation().getId()).collect(Collectors.toSet());
        Set<Long> skuIds = active.stream().map(a -> a.getSku().getId()).collect(Collectors.toSet());
        Map<String, Stock> rows = new HashMap<>();
        for (Stock stock : stocks.lockPairs(locationIds, skuIds)) {
            rows.put(pair(stock.getLocation().getId(), stock.getSku().getId()), stock);
        }
        for (Allocation allocation : active) {
            Objects.requireNonNull(rows.get(pair(allocation.getLocation().getId(), allocation.getSku().getId())))
                    .release(allocation.getQuantity(), now);
            allocation.release(now);
            allocation.getOrderLine().deallocate(allocation.getQuantity());
        }
    }

    private void releaseExpiredOf(long orderId, Instant now) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Заказ " + orderId));
        if (order.getStatus() != OrderStatus.ALLOCATED) {
            return;
        }
        List<Allocation> active = allocations.findActive(order);
        List<Allocation> expired = active.stream()
                .filter(a -> a.getExpiresAt() != null && a.getExpiresAt().isBefore(now))
                .toList();
        release(expired);
        if (expired.size() == active.size()) {
            order.transition(OrderStatus.NEW, CustomerOrder.from(OrderStatus.ALLOCATED));
        }
        log.info("Сняты резервы заказа {} по истечении срока: {}", order.getNumber(), expired.size());
    }

    private static void requireAllocatable(CustomerOrder order) {
        if (!ALLOCATABLE.contains(order.getStatus())) {
            throw new ConflictException("Заказ " + order.getNumber() + " в статусе " + order.getStatus()
                    + ": резервировать можно только новый или частично зарезервированный заказ");
        }
    }

    private static Collection<Long> skuIds(CustomerOrder order) {
        return order.getLines().stream().map(l -> l.getSku().getId()).collect(Collectors.toSet());
    }

    private static String pair(long locationId, long skuId) {
        return locationId + ":" + skuId;
    }

    /**
     * @param shortage сколько не хватило остатка: строка не обеспечена (FR-M5-04)
     */
    public record LineResult(int lineNo, String article, int ordered, int allocated, int shortage) {
    }

    public record AllocationResult(String order, OrderStatus status, boolean fullyAllocated, List<LineResult> lines) {

        static AllocationResult of(CustomerOrder order) {
            return new AllocationResult(order.getNumber(), order.getStatus(), order.isFullyAllocated(),
                    order.getLines().stream()
                            .map(l -> new LineResult(l.getLineNo(), l.getSku().getArticle(), l.getQuantityOrdered(),
                                    l.getQuantityAllocated(), l.unallocated()))
                            .toList());
        }
    }

    public record WaveAllocationResult(String wave, WaveStatus status, List<AllocationResult> orders) {
    }

    /**
     * @param recorded резерв в таблице остатков
     * @param active   сумма активных резервов
     */
    public record ReservationDiscrepancy(String location, String article, long recorded, long active) {
    }
}
