package io.github.r0mbaa.wms.core.shipping;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.allocation.AllocationService;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.inventory.DocumentRef;
import io.github.r0mbaa.wms.core.inventory.MovementType;
import io.github.r0mbaa.wms.core.inventory.Stock;
import io.github.r0mbaa.wms.core.inventory.StockLedger;
import io.github.r0mbaa.wms.core.inventory.StockLedger.Posting;
import io.github.r0mbaa.wms.core.inventory.StockRepository;
import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.order.OrderLine;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.task.Batch;
import io.github.r0mbaa.wms.core.task.Task;
import io.github.r0mbaa.wms.core.task.TaskService;
import io.github.r0mbaa.wms.core.task.TaskStatus;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Консолидация собранного и отгрузка (M11). Собранное лежит в таре до консолидации, затем в
 * зоне отгрузки до отгрузки; со склада оно списывается только движением {@code SHIP}
 * (FR-M11-05). Поэтому остаток мест тары и зоны отгрузки всегда равен отобранному, но не
 * отгруженному (INV-09).
 */
@Service
public class ShippingService {

    private final TaskService tasks;
    private final OrderService orders;
    private final AllocationService allocations;
    private final StockRepository stocks;
    private final StockLedger ledger;
    private final TopologyService topology;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;

    ShippingService(TaskService tasks, OrderService orders, AllocationService allocations, StockRepository stocks,
            StockLedger ledger, TopologyService topology, AuditLog audit, JdbcTemplate jdbc) {
        this.tasks = tasks;
        this.orders = orders;
        this.allocations = allocations;
        this.stocks = stocks;
        this.ledger = ledger;
        this.topology = topology;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /**
     * Консолидация после сборки (FR-M11-01): содержимое тары переходит в зону отгрузки, тара
     * освобождается для следующего задания, заказы получают {@code PACKED}.
     */
    @Transactional
    public Consolidation consolidate(String taskNumber) {
        Task task = tasks.get(taskNumber);
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new ConflictException("Задание " + task.getNumber() + " в статусе " + task.getStatus()
                    + ": раскладывать можно только собранное");
        }
        Location container = task.getBatch().getContainer().getLocation();
        List<Stock> contents = stocks.findPositiveAt(container);
        if (contents.isEmpty()) {
            throw new ConflictException("Тара " + task.getBatch().getContainer().getCode()
                    + " пуста: собранное по заданию " + task.getNumber() + " уже разложено");
        }
        Location shipping = topology.location(task.getWarehouse().shippingCode());
        DocumentRef document = new DocumentRef("TASK", task.getNumber());
        List<Consolidation.Moved> moved = new ArrayList<>();
        for (Stock stock : contents) {
            // Количество запоминается до проводки: она меняет эту же сущность остатка.
            int quantity = stock.getQuantity();
            ledger.post(new Posting(MovementType.TRANSFER, stock.getSku(), quantity, container, shipping, document,
                    "Консолидация"));
            moved.add(new Consolidation.Moved(stock.getSku().getArticle(), quantity));
        }
        List<String> packed = new ArrayList<>();
        for (Batch.BatchOrder entry : task.getBatch().getOrders()) {
            CustomerOrder order = entry.getOrder();
            if (order.getStatus() == OrderStatus.PICKED || order.getStatus() == OrderStatus.PARTIALLY_PICKED) {
                order.transition(OrderStatus.PACKED,
                        CustomerOrder.from(OrderStatus.PICKED, OrderStatus.PARTIALLY_PICKED));
                packed.add(order.getNumber());
            }
        }
        return new Consolidation(task.getNumber(), moved, packed);
    }

    /**
     * Отгрузка заказа (FR-M11-05): отобранное списывается со склада движениями {@code SHIP}.
     * Отгружается то, что собрано; недособранное фиксируется как недопоставка (FR-M11-06).
     */
    @Transactional
    public Shipment ship(String orderNumber) {
        CustomerOrder order = orders.get(orderNumber);
        if (order.getStatus() != OrderStatus.PACKED) {
            throw new ConflictException("Заказ " + order.getNumber() + " в статусе " + order.getStatus()
                    + ": отгрузить можно разложенный заказ (PACKED); сначала разложите тару его задания");
        }
        // INV-07: у отгружаемого заказа не остаётся активных резервов.
        if (!allocations.activeOf(order).isEmpty()) {
            throw new ConflictException("По заказу " + order.getNumber()
                    + " остались незакрытые резервы: завершите сборку или снимите резерв");
        }
        Location shipping = topology.location(order.getWarehouse().shippingCode());
        DocumentRef document = new DocumentRef("ORDER", order.getNumber());
        List<Shipment.Line> lines = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            int quantity = line.getQuantityPicked() - line.getQuantityShipped();
            if (quantity > 0) {
                ledger.post(new Posting(MovementType.SHIP, line.getSku(), quantity, shipping, null, document, null));
                line.ship(quantity);
            }
            lines.add(new Shipment.Line(line.getLineNo(), line.getSku().getArticle(), line.getQuantityOrdered(),
                    line.getQuantityShipped()));
        }
        order.transition(OrderStatus.SHIPPED, CustomerOrder.from(OrderStatus.PACKED));
        List<Shipment.Line> shortages = lines.stream().filter(l -> l.shipped() < l.ordered()).toList();
        if (!shortages.isEmpty()) {
            audit.record("ORDER_SHIPPED_SHORT", "ORDER", order.getNumber(), null,
                    Map.of("shortages", shortages));
        }
        return new Shipment(order.getNumber(), order.getStatus(), lines, shortages);
    }

    /**
     * Сверка INV-09 по SKU: остаток в таре и зоне отгрузки против отобранного, но не
     * отгруженного по заказам. Пустой список — собранное не потерялось и не задвоилось.
     */
    @Transactional(readOnly = true)
    public List<InTransitDiscrepancy> checkInTransit() {
        return jdbc.query("""
                with in_transit as (
                    select s.sku_id, sum(s.quantity) as quantity
                    from stock s join location l on l.id = s.location_id
                    where l.type in ('CONTAINER', 'SHIPPING')
                    group by s.sku_id
                ), owed as (
                    select l.sku_id, sum(l.quantity_picked - l.quantity_shipped) as quantity
                    from order_line l
                    group by l.sku_id
                )
                select k.article, coalesce(t.quantity, 0), coalesce(o.quantity, 0)
                from in_transit t
                full join owed o on o.sku_id = t.sku_id
                join sku k on k.id = coalesce(t.sku_id, o.sku_id)
                where coalesce(t.quantity, 0) <> coalesce(o.quantity, 0)
                order by k.article
                """, (rs, n) -> new InTransitDiscrepancy(rs.getString(1), rs.getLong(2), rs.getLong(3)));
    }

    /**
     * @param moved  что переложено из тары в зону отгрузки
     * @param packed заказы, готовые к отгрузке
     */
    public record Consolidation(String task, List<Moved> moved, List<String> packed) {

        public record Moved(String article, int quantity) {
        }
    }

    /** @param shortages строки, отгруженные не полностью: недопоставка (FR-M11-06) */
    public record Shipment(String order, OrderStatus status, List<Line> lines, List<Line> shortages) {

        public record Line(int lineNo, String article, int ordered, int shipped) {
        }
    }

    /**
     * @param inTransit в таре и зоне отгрузки
     * @param owed      отобрано, но не отгружено по заказам
     */
    public record InTransitDiscrepancy(String article, long inTransit, long owed) {
    }
}
