package io.github.r0mbaa.wms.core.order;

import java.time.Instant;
import java.util.List;

public final class OrderViews {

    private OrderViews() {
    }

    public record OrderSummary(String number, String warehouse, String counterparty, int priority, Instant deadlineAt,
            String carrier, String direction, boolean hot, OrderStatus status, String wave, Instant createdAt) {

        public static OrderSummary from(CustomerOrder o) {
            return new OrderSummary(o.getNumber(), o.getWarehouse().getCode(), o.getCounterparty(), o.getPriority(),
                    o.getDeadlineAt(), o.getCarrier(), o.getDirection(), o.isHot(), o.getStatus(),
                    o.getWave() == null ? null : o.getWave().getNumber(), o.getCreatedAt());
        }
    }

    /**
     * @param totalWeightKg вес заказа целиком: входит в ограничение тары при батчинге (§8.3)
     * @param totalVolumeM3 объём заказа целиком
     */
    public record OrderView(OrderSummary header, double totalWeightKg, double totalVolumeM3, List<LineView> lines) {

        public static OrderView from(CustomerOrder o) {
            return new OrderView(OrderSummary.from(o), o.totalWeightKg(), o.totalVolumeM3(),
                    o.getLines().stream().map(LineView::from).toList());
        }
    }

    public record LineView(int lineNo, String article, String name, int ordered, int allocated, int picked,
            int shipped) {

        static LineView from(OrderLine l) {
            return new LineView(l.getLineNo(), l.getSku().getArticle(), l.getSku().getName(), l.getQuantityOrdered(),
                    l.getQuantityAllocated(), l.getQuantityPicked(), l.getQuantityShipped());
        }
    }
}
