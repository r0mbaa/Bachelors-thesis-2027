package io.github.r0mbaa.wms.core.catalog;

import io.github.r0mbaa.wms.core.catalog.CatalogService.BarcodeSpec;
import java.util.List;

public record SkuView(
        String article,
        String name,
        String uom,
        double lengthM,
        double widthM,
        double heightM,
        double weightKg,
        double volumeM3,
        StorageClass storageClass,
        String abcClass,
        String xyzClass,
        List<BarcodeSpec> barcodes,
        String qrPayload) {

    public static SkuView from(Sku s) {
        return new SkuView(s.getArticle(), s.getName(), s.getUom(), s.getLengthM(), s.getWidthM(), s.getHeightM(),
                s.getWeightKg(), s.getVolumeM3(), s.getStorageClass(), s.getAbcClass(), s.getXyzClass(),
                s.getBarcodes().stream().map(b -> new BarcodeSpec(b.getBarcode(), b.getType())).toList(),
                s.qrPayload());
    }
}
