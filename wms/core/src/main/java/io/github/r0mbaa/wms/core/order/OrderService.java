package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.order.CustomerOrder.OrderHeader;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Приём заказов (FR-M5-01, FR-M5-02), проверка обеспеченности (FR-M5-04) и отмена (FR-M5-07). */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final TopologyService topology;
    private final CatalogService catalog;
    private final ApplicationEventPublisher events;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    OrderService(OrderRepository orders, TopologyService topology, CatalogService catalog,
            ApplicationEventPublisher events, AuditLog audit, JdbcTemplate jdbc, Clock clock) {
        this.orders = orders;
        this.topology = topology;
        this.catalog = catalog;
        this.events = events;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public CustomerOrder create(String warehouseCode, OrderHeader header, List<LineSpec> lines) {
        return create(topology.warehouse(warehouseCode), header, lines);
    }

    /** Импорт CSV целиком или никак: ошибка в любой строке отменяет весь файл. */
    @Transactional
    public List<CustomerOrder> importCsv(String warehouseCode, String csv) {
        Warehouse warehouse = topology.warehouse(warehouseCode);
        return OrderCsvParser.parse(csv).stream()
                .map(parsed -> create(warehouse, parsed.header(), parsed.lines()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerOrder get(String number) {
        return orders.findByNumber(number.strip())
                .orElseThrow(() -> new NotFoundException("Заказ " + number + " не найден"));
    }

    @Transactional(readOnly = true)
    public Page<CustomerOrder> search(String warehouseCode, OrderStatus status, int page, int size) {
        return orders.search(topology.warehouse(warehouseCode), status, PageRequest.of(page, size));
    }

    /**
     * Обеспеченность строк заказа остатками (FR-M5-04): сколько уже зарезервировано и сколько
     * свободно в доступных ячейках зоны отбора.
     */
    @Transactional(readOnly = true)
    public List<LineCoverage> coverage(String number) {
        CustomerOrder order = get(number);
        return order.getLines().stream().map(line -> {
            int free = Objects.requireNonNull(jdbc.queryForObject("""
                    select coalesce(sum(s.quantity - s.reserved_quantity), 0)
                    from stock s join location l on l.id = s.location_id
                    where l.warehouse_id = ? and s.sku_id = ? and l.active and not l.blocked and l.type = 'PICKING'
                    """, Integer.class, order.getWarehouse().getId(), line.getSku().getId()));
            return LineCoverage.of(line, free);
        }).toList();
    }

    /**
     * Отмена до начала сборки (FR-M5-07). Резервы и строки неначатых заданий снимают слушатели
     * {@link OrderCancelled} в той же транзакции.
     */
    @Transactional
    public CustomerOrder cancel(String number, String reason) {
        CustomerOrder order = get(number);
        OrderStatus before = order.getStatus();
        order.cancel();
        events.publishEvent(new OrderCancelled(order.getId(), order.getNumber()));
        audit.record("ORDER_CANCELLED", "ORDER", order.getNumber(), before, reason);
        return order;
    }

    private CustomerOrder create(Warehouse warehouse, OrderHeader header, List<LineSpec> lines) {
        if (header.number() == null || header.number().isBlank()) {
            throw new IllegalArgumentException("Укажите номер заказа");
        }
        if (orders.existsByNumber(header.number().strip())) {
            throw new ConflictException("Заказ " + header.number().strip() + " уже принят: повторная загрузка не нужна");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("В заказе " + header.number() + " нет строк");
        }
        OrderHeader normalized = new OrderHeader(header.number().strip(), header.counterparty().strip(),
                header.priority(), header.deadlineAt(), header.carrier(), header.direction(), header.hot());
        CustomerOrder order = new CustomerOrder(warehouse, normalized, clock.instant());
        Set<String> seen = new HashSet<>();
        for (LineSpec line : lines) {
            var sku = catalog.resolve(line.sku());
            if (!seen.add(sku.getArticle())) {
                throw new IllegalArgumentException("Товар " + sku.getArticle() + " указан в заказе "
                        + normalized.number() + " дважды: объедините строки");
            }
            order.addLine(sku, line.quantity());
        }
        return orders.save(order);
    }

    public record LineSpec(String sku, int quantity) {
    }

    public enum CoverageStatus {
        /** Строка полностью зарезервирована. */
        ALLOCATED,
        /** Свободного остатка хватает на незарезервированную часть. */
        COVERED,
        PARTIAL,
        /** Свободного остатка нет: строка не обеспечена. */
        SHORT
    }

    /**
     * @param free свободно в доступных ячейках зоны отбора
     */
    public record LineCoverage(int lineNo, String article, int ordered, int allocated, int free,
            CoverageStatus status) {

        static LineCoverage of(OrderLine line, int free) {
            int need = line.unallocated();
            CoverageStatus status = need == 0 ? CoverageStatus.ALLOCATED
                    : free >= need ? CoverageStatus.COVERED
                    : free > 0 ? CoverageStatus.PARTIAL
                    : CoverageStatus.SHORT;
            return new LineCoverage(line.getLineNo(), line.getSku().getArticle(), line.getQuantityOrdered(),
                    line.getQuantityAllocated(), free, status);
        }
    }
}
