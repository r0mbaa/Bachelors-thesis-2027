package io.github.r0mbaa.wms.core.catalog;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Справочник SKU (M2). Изменения карточки пишутся в аудит со старым и новым значением. */
@Service
public class CatalogService {

    private static final String AUDIT_ENTITY = "SKU";

    private final SkuRepository skus;
    private final AuditLog audit;
    private final Clock clock;

    CatalogService(SkuRepository skus, AuditLog audit, Clock clock) {
        this.skus = skus;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Sku create(String article, SkuDescription description, List<BarcodeSpec> barcodes) {
        Sku sku = new Sku(article, clock.instant());
        if (skus.existsByArticle(sku.getArticle())) {
            throw new ConflictException("Товар с артикулом " + sku.getArticle() + " уже есть в справочнике");
        }
        description.applyTo(sku);
        for (BarcodeSpec barcode : barcodes) {
            requireFreeBarcode(barcode.barcode());
            sku.addBarcode(barcode.barcode(), barcode.type());
        }
        skus.save(sku);
        audit.record("SKU_CREATED", AUDIT_ENTITY, sku.getArticle(), null, SkuView.from(sku));
        return sku;
    }

    @Transactional
    public Sku update(String article, SkuDescription description) {
        Sku sku = get(article);
        SkuView before = SkuView.from(sku);
        description.applyTo(sku);
        audit.record("SKU_UPDATED", AUDIT_ENTITY, sku.getArticle(), before, SkuView.from(sku));
        return sku;
    }

    @Transactional
    public Sku addBarcode(String article, BarcodeSpec barcode) {
        Sku sku = get(article);
        requireFreeBarcode(barcode.barcode());
        sku.addBarcode(barcode.barcode(), barcode.type());
        audit.record("SKU_BARCODE_ADDED", AUDIT_ENTITY, sku.getArticle(), null, barcode);
        return sku;
    }

    @Transactional
    public Sku removeBarcode(String article, String barcode) {
        Sku sku = get(article);
        if (!sku.removeBarcode(barcode)) {
            throw new NotFoundException("У товара " + sku.getArticle() + " нет штрихкода " + barcode);
        }
        audit.record("SKU_BARCODE_REMOVED", AUDIT_ENTITY, sku.getArticle(), barcode, null);
        return sku;
    }

    @Transactional(readOnly = true)
    public Sku get(String article) {
        return skus.findByArticle(article.strip())
                .orElseThrow(() -> new NotFoundException("Товар с артикулом " + article + " не найден в справочнике"));
    }

    @Transactional(readOnly = true)
    public Page<Sku> search(String query, int page, int size) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
        return skus.search(pattern, PageRequest.of(page, size));
    }

    /**
     * Находит товар по отсканированному или введённому коду: QR системы {@code SKU:артикул},
     * штрихкод производителя или сам артикул. Так приёмка работает и с заводской упаковкой без
     * наклеенного QR.
     */
    @Transactional(readOnly = true)
    public Sku resolve(String scanned) {
        return find(scanned).orElseThrow(() -> new NotFoundException("Товар по коду '" + scanned.strip()
                + "' не найден: отсканируйте QR товара или проверьте штрихкод в справочнике"));
    }

    /** То же, что {@link #resolve}, но без исключения: для разбора скана, который может оказаться не товаром. */
    @Transactional(readOnly = true)
    public Optional<Sku> find(String scanned) {
        String code = scanned == null ? "" : scanned.strip();
        if (code.regionMatches(true, 0, "SKU:", 0, 4)) {
            return skus.findByArticle(((QrPayload.Sku) QrPayload.parse(code)).article());
        }
        return skus.findByBarcode(code).or(() -> skus.findByArticle(code));
    }

    private void requireFreeBarcode(String barcode) {
        skus.findByBarcode(barcode).ifPresent(owner -> {
            throw new ConflictException("Штрихкод " + barcode + " уже принадлежит товару " + owner.getArticle()
                    + ": один штрихкод должен однозначно указывать на один товар");
        });
    }

    /**
     * Изменяемая часть карточки SKU.
     *
     * @param volumeM3 {@code null} — объём считается по габаритам
     */
    public record SkuDescription(String name, String uom, double lengthM, double widthM, double heightM,
            double weightKg, Double volumeM3, StorageClass storageClass) {

        void applyTo(Sku sku) {
            sku.describe(name, uom, lengthM, widthM, heightM, weightKg, volumeM3,
                    storageClass == null ? StorageClass.NORMAL : storageClass);
        }
    }

    public record BarcodeSpec(String barcode, BarcodeType type) {
    }
}
