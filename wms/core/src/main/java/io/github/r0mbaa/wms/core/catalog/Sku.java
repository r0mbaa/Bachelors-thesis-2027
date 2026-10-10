package io.github.r0mbaa.wms.core.catalog;

import io.github.r0mbaa.wms.shared.marking.QrPayload;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Единица номенклатуры (FR-M2-01, FR-M2-02). Идентифицирует вид товара, а не экземпляр: QR
 * товара кодирует артикул, поэтому количество при сканировании вводится вручную (FR-M3-06a).
 */
@Entity
@Table(name = "sku")
public class Sku {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String article;

    private String name;

    /** Базовая единица измерения, в ней ведутся все количества. */
    private String uom;

    @Column(name = "length_m")
    private double lengthM;

    @Column(name = "width_m")
    private double widthM;

    @Column(name = "height_m")
    private double heightM;

    @Column(name = "weight_kg")
    private double weightKg;

    @Column(name = "volume_m3")
    private double volumeM3;

    @Enumerated(EnumType.STRING)
    private StorageClass storageClass;

    private String abcClass;

    private String xyzClass;

    private Instant createdAt;

    @OneToMany(mappedBy = "sku", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SkuBarcode> barcodes = new ArrayList<>();

    @Version
    private long version;

    protected Sku() {
    }

    public Sku(String article, Instant createdAt) {
        QrPayload.Sku qr = new QrPayload.Sku(article == null ? null : article.strip());
        this.article = qr.article();
        this.createdAt = createdAt;
    }

    /**
     * @param volumeM3 {@code null} — объём считается как Д × Ш × В; иначе задаётся явно, например
     *                 для товара неправильной формы
     */
    void describe(String name, String uom, double lengthM, double widthM, double heightM, double weightKg,
            Double volumeM3, StorageClass storageClass) {
        requirePositive("Длина", lengthM);
        requirePositive("Ширина", widthM);
        requirePositive("Высота", heightM);
        requirePositive("Вес", weightKg);
        double volume = volumeM3 == null ? lengthM * widthM * heightM : volumeM3;
        requirePositive("Объём", volume);
        this.name = name.strip();
        this.uom = uom.strip();
        this.lengthM = lengthM;
        this.widthM = widthM;
        this.heightM = heightM;
        this.weightKg = weightKg;
        this.volumeM3 = volume;
        this.storageClass = storageClass;
    }

    void addBarcode(String barcode, BarcodeType type) {
        barcodes.add(new SkuBarcode(this, barcode, type));
    }

    boolean removeBarcode(String barcode) {
        return barcodes.removeIf(b -> b.getBarcode().equals(barcode));
    }

    public Long getId() {
        return id;
    }

    public String getArticle() {
        return article;
    }

    public String getName() {
        return name;
    }

    public String getUom() {
        return uom;
    }

    public double getLengthM() {
        return lengthM;
    }

    public double getWidthM() {
        return widthM;
    }

    public double getHeightM() {
        return heightM;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public double getVolumeM3() {
        return volumeM3;
    }

    public StorageClass getStorageClass() {
        return storageClass;
    }

    public String getAbcClass() {
        return abcClass;
    }

    public String getXyzClass() {
        return xyzClass;
    }

    public List<SkuBarcode> getBarcodes() {
        return List.copyOf(barcodes);
    }

    public String qrPayload() {
        return new QrPayload.Sku(article).encode();
    }

    private static void requirePositive(String what, double value) {
        if (!(value > 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(what + " товара: нужно положительное число, задано " + value);
        }
    }
}
