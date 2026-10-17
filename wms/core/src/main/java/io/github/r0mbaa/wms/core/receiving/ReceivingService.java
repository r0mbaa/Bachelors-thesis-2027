package io.github.r0mbaa.wms.core.receiving;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.admin.CurrentUser;
import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.inventory.DocumentRef;
import io.github.r0mbaa.wms.core.inventory.Movement;
import io.github.r0mbaa.wms.core.inventory.MovementType;
import io.github.r0mbaa.wms.core.inventory.StockLedger;
import io.github.r0mbaa.wms.core.inventory.StockLedger.Posting;
import io.github.r0mbaa.wms.core.layout.LayoutService;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.layout.model.Point;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Приёмка и размещение (M3, процесс §6.2): документ прихода, приёмка по факту в зону приёмки,
 * рекомендация ячейки и размещение двумя сканированиями.
 */
@Service
public class ReceivingService {

    private static final String DOCUMENT = "RECEIPT";

    /** Код без префикса, похожий на адрес ячейки: так распознаётся введённая вручную полка. */
    private static final Pattern CELL_ADDRESS = Pattern.compile("[A-Z][A-Z0-9]{0,5}-R\\d{2}-.*");

    private final ReceiptRepository receipts;
    private final TopologyService topology;
    private final CatalogService catalog;
    private final LayoutService layouts;
    private final StockLedger ledger;
    private final PutawayAdvisor advisor;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    ReceivingService(ReceiptRepository receipts, TopologyService topology, CatalogService catalog,
            LayoutService layouts, StockLedger ledger, PutawayAdvisor advisor, AuditLog audit,
            CurrentUser currentUser, JdbcTemplate jdbc, Clock clock) {
        this.receipts = receipts;
        this.topology = topology;
        this.catalog = catalog;
        this.layouts = layouts;
        this.ledger = ledger;
        this.advisor = advisor;
        this.audit = audit;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Регистрирует ожидаемую поставку (FR-M3-01).
     *
     * @param number номер документа поставщика; если не задан, присваивается {@code PR-000001}
     */
    @Transactional
    public Receipt create(String warehouseCode, String number, String supplier, List<ExpectedLine> lines) {
        Warehouse warehouse = topology.warehouse(warehouseCode);
        String receiptNumber = number == null || number.isBlank()
                ? String.format(Locale.ROOT, "PR-%06d", jdbc.queryForObject("select nextval('receipt_number_seq')", Long.class))
                : number.strip();
        if (receipts.existsByNumber(receiptNumber)) {
            throw new ConflictException("Приход с номером " + receiptNumber + " уже зарегистрирован");
        }
        Receipt receipt = new Receipt(warehouse, receiptNumber, supplier.strip(), currentUser.username(), clock.instant());
        for (ExpectedLine line : lines) {
            receipt.expect(catalog.resolve(line.sku()), line.quantity());
        }
        receipts.save(receipt);
        audit.record("RECEIPT_CREATED", DOCUMENT, receiptNumber, null,
                Map.of("supplier", receipt.getSupplier(), "lines", lines));
        return receipt;
    }

    @Transactional(readOnly = true)
    public Receipt get(String number) {
        return receipts.findByNumber(number.strip())
                .orElseThrow(() -> new NotFoundException("Приход " + number + " не найден"));
    }

    @Transactional(readOnly = true)
    public List<Receipt> list(String warehouseCode, boolean openOnly) {
        List<ReceiptStatus> statuses = openOnly
                ? List.of(ReceiptStatus.EXPECTED, ReceiptStatus.RECEIVING)
                : List.of(ReceiptStatus.values());
        return receipts.findByWarehouseAndStatusInOrderByCreatedAtDesc(topology.warehouse(warehouseCode), statuses);
    }

    /**
     * Приёмка по факту (§6.2, шаги 2–3): товар поступает в зону приёмки движением {@code RECEIPT},
     * количество засчитывается в строку документа.
     */
    @Transactional
    public Receipt receive(String number, String skuCode, int quantity) {
        Receipt receipt = get(number);
        receipt.requireOpen();
        Sku sku = catalog.resolve(skuCode);
        Location receiving = topology.location(receipt.getWarehouse().receivingCode());
        ledger.post(new Posting(MovementType.RECEIPT, sku, quantity, null, receiving, document(receipt), null));
        receipt.receive(sku, quantity);
        return receipt;
    }

    @Transactional(readOnly = true)
    public Optional<String> recommend(String number, String skuCode, int quantity) {
        Receipt receipt = get(number);
        return advisor.recommend(receipt.getWarehouse(), catalog.resolve(skuCode), quantity, depot(receipt));
    }

    /**
     * Размещение двумя сканированиями (FR-M3-06): товар и полка в любом порядке. Порядок
     * «сначала товар» основной; обратный принимается, но в ответе явно сказано, что распознано
     * (FR-M3-06). Количество вводится вручную: QR товара кодирует вид, а не экземпляр (FR-M3-06a).
     *
     * <p>Размещение не в рекомендованную ячейку допустимо, но отклонение фиксируется в
     * комментарии движения (FR-M3-05).
     */
    @Transactional
    public PutawayResult putaway(String number, List<String> scans, int quantity) {
        if (scans == null || scans.size() != 2) {
            throw new IllegalArgumentException("Для размещения нужны два сканирования: товар и полка");
        }
        Receipt receipt = get(number);
        Scanned first = classify(scans.get(0));
        Scanned second = classify(scans.get(1));
        if (first.isLocation() == second.isLocation()) {
            throw new IllegalArgumentException(first.isLocation()
                    ? "Отсканированы две полки: отсканируйте QR товара и затем QR полки"
                    : "Отсканированы два товара: отсканируйте QR товара и затем QR полки");
        }
        boolean reversed = first.isLocation();
        Sku sku = reversed ? second.sku() : first.sku();
        Location cell = reversed ? first.location() : second.location();
        if (!cell.isCell()) {
            throw new IllegalArgumentException(cell.getCode() + " — не ячейка стеллажа: отсканируйте QR на полке");
        }

        ReceiptLine line = receipt.line(sku).orElseThrow(() -> new ConflictException("Товара " + sku.getArticle()
                + " нет в приходе " + receipt.getNumber() + ": сначала примите его"));
        String recommended = advisor.recommend(receipt.getWarehouse(), sku, quantity, depot(receipt)).orElse(null);
        boolean deviation = recommended != null && !recommended.equals(cell.getCode());
        line.putAway(quantity);
        Movement movement = ledger.post(new Posting(MovementType.PUTAWAY, sku, quantity,
                topology.location(receipt.getWarehouse().receivingCode()), cell, document(receipt),
                deviation ? "Размещено не в рекомендованную ячейку " + recommended : null));

        String recognized = reversed
                ? "Распознано: сначала полка " + cell.getCode() + ", затем товар " + sku.getArticle()
                : "Распознано: товар " + sku.getArticle() + ", полка " + cell.getCode();
        return new PutawayResult(movement.getId(), sku.getArticle(), cell.getCode(), quantity, recommended, deviation,
                reversed, recognized, line.awaitingPutaway());
    }

    /** Закрывает приёмку; расхождения видны в строках документа (FR-M3-02). */
    @Transactional
    public Receipt close(String number) {
        Receipt receipt = get(number);
        receipt.close(clock.instant());
        audit.record("RECEIPT_CLOSED", DOCUMENT, receipt.getNumber(), null, receipt.getLines().stream()
                .filter(l -> l.discrepancy() != 0)
                .map(l -> Map.of("sku", l.getSku().getArticle(), "expected", l.getQuantityExpected(),
                        "received", l.getQuantityReceived()))
                .toList());
        return receipt;
    }

    private Scanned classify(String scan) {
        String code = scan == null ? "" : scan.strip();
        String upper = code.toUpperCase(Locale.ROOT);
        if (upper.startsWith("LOC:") || CELL_ADDRESS.matcher(upper).matches()) {
            return new Scanned(null, topology.resolveLocation(code));
        }
        // Код без префикса может оказаться и местом без этикетки, например зоной отгрузки.
        return catalog.find(code).map(sku -> new Scanned(sku, null))
                .or(() -> topology.findLocation(code).map(location -> new Scanned(null, location)))
                .orElseThrow(() -> new NotFoundException("Код '" + code
                        + "' не распознан ни как товар, ни как полка: отсканируйте QR на товаре или на полке"));
    }

    private Point depot(Receipt receipt) {
        return receipt.getWarehouse().getLayoutVersion() == 0
                ? new Point(0, 0)
                : layouts.current(receipt.getWarehouse().getCode()).depot();
    }

    private static DocumentRef document(Receipt receipt) {
        return new DocumentRef(DOCUMENT, receipt.getNumber());
    }

    private record Scanned(Sku sku, Location location) {

        boolean isLocation() {
            return location != null;
        }
    }

    public record ExpectedLine(String sku, int quantity) {
    }

    /**
     * @param recommended     ячейка, которую рекомендовала система; {@code null}, если подходящей не нашлось
     * @param deviation       размещено не в рекомендованную ячейку (FR-M3-05)
     * @param orderReversed   сначала отсканирована полка, затем товар
     * @param recognized      что система распознала: показывается кладовщику
     * @param awaitingPutaway сколько этого товара по приходу ещё ждёт размещения
     */
    public record PutawayResult(long movementId, String article, String location, int quantity, String recommended,
            boolean deviation, boolean orderReversed, String recognized, int awaitingPutaway) {
    }
}
