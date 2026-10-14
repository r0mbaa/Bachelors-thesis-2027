package io.github.r0mbaa.wms.core.inventory;

import java.time.Instant;

public final class InventoryViews {

    private InventoryViews() {
    }

    /**
     * @param available свободно для резерва и перемещения: quantity − reserved (FR-M4-02)
     */
    public record StockView(String location, String zone, String article, String skuName, String uom,
            int quantity, int reserved, int available) {

        public static StockView from(Stock s) {
            return new StockView(s.getLocation().getCode(),
                    s.getLocation().getZone() == null ? null : s.getLocation().getZone().getCode(),
                    s.getSku().getArticle(), s.getSku().getName(), s.getSku().getUom(),
                    s.getQuantity(), s.getReservedQuantity(), s.available());
        }
    }

    public record MovementView(long id, MovementType type, String article, int quantity, String from, String to,
            String documentType, String documentId, String username, Instant occurredAt, String comment) {

        public static MovementView from(Movement m) {
            return new MovementView(m.getId(), m.getType(), m.getSku().getArticle(), m.getQuantity(),
                    m.getLocationFrom() == null ? null : m.getLocationFrom().getCode(),
                    m.getLocationTo() == null ? null : m.getLocationTo().getCode(),
                    m.getDocumentType(), m.getDocumentId(), m.getUsername(), m.getOccurredAt(), m.getComment());
        }
    }
}
