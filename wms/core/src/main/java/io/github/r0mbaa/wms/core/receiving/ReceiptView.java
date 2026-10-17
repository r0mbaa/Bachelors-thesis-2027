package io.github.r0mbaa.wms.core.receiving;

import java.time.Instant;
import java.util.List;

public record ReceiptView(String number, String warehouse, String supplier, ReceiptStatus status, String createdBy,
        Instant createdAt, Instant closedAt, List<LineView> lines) {

    static ReceiptView from(Receipt r) {
        return new ReceiptView(r.getNumber(), r.getWarehouse().getCode(), r.getSupplier(), r.getStatus(),
                r.getCreatedBy(), r.getCreatedAt(), r.getClosedAt(),
                r.getLines().stream().map(LineView::from).toList());
    }

    /**
     * @param discrepancy принято минус ожидалось: больше нуля — излишек, меньше — недостача
     */
    public record LineView(String article, String name, int expected, int received, int putAway,
            int awaitingPutaway, int discrepancy) {

        static LineView from(ReceiptLine l) {
            return new LineView(l.getSku().getArticle(), l.getSku().getName(), l.getQuantityExpected(),
                    l.getQuantityReceived(), l.getQuantityPutAway(), l.awaitingPutaway(), l.discrepancy());
        }
    }
}
