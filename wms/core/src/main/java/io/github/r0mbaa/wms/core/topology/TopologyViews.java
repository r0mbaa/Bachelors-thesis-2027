package io.github.r0mbaa.wms.core.topology;

import io.github.r0mbaa.wms.layout.model.Facing;
import java.time.Instant;

/** Представления топологии в API. Собираются из сущностей с уже загруженными связями. */
public final class TopologyViews {

    private TopologyViews() {
    }

    public record WarehouseView(String code, String name, double defaultSpeedMps, long layoutVersion,
            String receivingLocation, String shippingLocation, Instant createdAt) {

        public static WarehouseView from(Warehouse w) {
            return new WarehouseView(w.getCode(), w.getName(), w.getDefaultSpeedMps(), w.getLayoutVersion(),
                    w.receivingCode(), w.shippingCode(), w.getCreatedAt());
        }
    }

    public record ZoneView(String code, String name, LocationType type, StoragePolicy storagePolicy) {

        public static ZoneView from(Zone z) {
            return new ZoneView(z.getCode(), z.getName(), z.getType(), z.getStoragePolicy());
        }
    }

    /**
     * @param z      высота полки над полом, м
     * @param access точка на лицевой линии ряда, куда подходит сборщик
     */
    public record LocationView(
            String code,
            LocationType type,
            String zone,
            Integer row,
            Integer section,
            Integer level,
            Integer position,
            Double x,
            Double y,
            Double z,
            Point access,
            Facing facing,
            Double maxWeightKg,
            Double maxVolumeM3,
            boolean blocked,
            String blockReason,
            boolean active,
            String qrPayload) {

        public static LocationView from(Location l) {
            return new LocationView(
                    l.getCode(), l.getType(), l.getZone() == null ? null : l.getZone().getCode(),
                    l.getRowNo(), l.getSectionNo(), l.getLevelNo(), l.getPositionNo(),
                    l.getX(), l.getY(), l.getZ(),
                    l.getAccessX() == null ? null : new Point(l.getAccessX(), l.getAccessY()),
                    l.getFacing(), l.getMaxWeightKg(), l.getMaxVolumeM3(),
                    l.isBlocked(), l.getBlockReason(), l.isActive(), l.qrPayload());
        }
    }

    public record Point(double x, double y) {
    }
}
