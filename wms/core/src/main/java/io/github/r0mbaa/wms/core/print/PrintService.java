package io.github.r0mbaa.wms.core.print;

import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.task.Container;
import io.github.r0mbaa.wms.core.task.ContainerService;
import io.github.r0mbaa.wms.core.task.ContainerType;
import io.github.r0mbaa.wms.core.task.TaskService;
import io.github.r0mbaa.wms.core.task.Worker;
import io.github.r0mbaa.wms.core.task.WorkerService;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Печатные формы: пакетные QR-этикетки ячеек, товаров, тары и бейджей сотрудников (FR-M1-12,
 * §11.3) и маршрутный лист задания (FR-M11-03).
 */
@Service
public class PrintService {

    /** Сто листов за раз: целый склад печатается по рядам или зонам, а не одним файлом. */
    static final int MAX_LABELS = 100 * LabelSheet.COLUMNS * LabelSheet.ROWS;

    private final TopologyService topology;
    private final CatalogService catalog;
    private final ContainerService containers;
    private final WorkerService workers;
    private final TaskService tasks;
    private final ZoneId zone;

    PrintService(TopologyService topology, CatalogService catalog, ContainerService containers,
            WorkerService workers, TaskService tasks, @Value("${wms.print.zone:Europe/Moscow}") ZoneId zone) {
        this.topology = topology;
        this.catalog = catalog;
        this.containers = containers;
        this.workers = workers;
        this.tasks = tasks;
        this.zone = zone;
    }

    /** Этикетки ячеек склада, ряда или зоны в порядке адресов: так их удобно клеить по стеллажу. */
    @Transactional(readOnly = true)
    public byte[] locationLabels(String warehouseCode, Integer row, String zoneCode) {
        List<Location> cells = topology.search(warehouseCode, row, zoneCode, null, null, 0, MAX_LABELS + 1)
                .getContent().stream()
                .filter(Location::isCell)
                .toList();
        return render(cells.stream().map(PrintService::label).toList(), "ячеек");
    }

    /** Этикетки товаров (QR вида товара, FR-M3-06a); пустой список — весь справочник. */
    @Transactional(readOnly = true)
    public byte[] skuLabels(List<String> articles) {
        List<Sku> skus = articles == null || articles.isEmpty()
                ? catalog.search(null, 0, MAX_LABELS + 1).getContent()
                : articles.stream().map(catalog::resolve).toList();
        return render(skus.stream().map(sku -> new Label(sku.qrPayload(), sku.getArticle(),
                List.of(sku.getName(), sku.getUom()), sku.getArticle())).toList(), "товаров");
    }

    @Transactional(readOnly = true)
    public byte[] containerLabels(String warehouseCode) {
        return render(containers.list(warehouseCode).stream().map(PrintService::label).toList(), "единиц тары");
    }

    /** Бейджи сборщиков: по QR {@code WRK:…} и PIN сборщик входит в терминал (FR-M10-02). */
    @Transactional(readOnly = true)
    public byte[] workerBadges(String warehouseCode) {
        return render(workers.list(warehouseCode).stream().map(PrintService::label).toList(), "бейджей");
    }

    @Transactional(readOnly = true)
    public byte[] routeSheet(String taskNumber) {
        return RouteSheet.render(tasks.view(taskNumber), zone);
    }

    private static byte[] render(List<Label> labels, String what) {
        if (labels.size() > MAX_LABELS) {
            throw new IllegalArgumentException("За раз печатается не больше " + MAX_LABELS + " этикеток " + what
                    + ": выберите ряд или зону");
        }
        return LabelSheet.render(labels);
    }

    private static Label label(Location cell) {
        return new Label(cell.qrPayload(),
                String.format(Locale.ROOT, "Ряд %02d · секция %02d", cell.getRowNo(), cell.getSectionNo()),
                List.of("Ярус " + cell.getLevelNo() + " · позиция " + cell.getPositionNo(),
                        cell.getZone() == null ? "" : "Зона " + cell.getZone().getCode()),
                cell.getCode());
    }

    private static Label label(Container container) {
        String kind = container.getType() == ContainerType.CART ? "Тележка" : "Короб";
        return new Label(container.qrPayload(), kind + " " + container.getCode(),
                List.of("Отделений: " + container.getSlots(),
                        String.format(Locale.ROOT, "До %.0f кг, %.2f м³", container.getMaxWeightKg(), container.getMaxVolumeM3())),
                container.getCode());
    }

    private static Label label(Worker worker) {
        return new Label(new QrPayload.Worker(worker.getCode()).encode(), worker.getUser().getFullName(),
                List.of("Сборщик", "Вход: бейдж и PIN"), worker.getCode());
    }
}
