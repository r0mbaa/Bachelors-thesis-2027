package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.topology.Location;
import java.util.function.Predicate;

/**
 * Что показывает терминал сборщика: задание, прогресс (FR-M10-05) и текущий шаг крупно
 * (FR-M10-03). Маршрут идёт строго по порядку, поэтому шаг всегда один.
 *
 * @param current {@code null}, когда все шаги пройдены и задание можно завершать
 */
public record TerminalView(String task, TaskStatus status, String container, int stepsTotal, long stepsDone,
        CurrentStep current) {

    static TerminalView of(Task task, Predicate<TaskStep> shelfScanRequired) {
        return new TerminalView(task.getNumber(), task.getStatus(), task.getBatch().getContainer().getCode(),
                task.getSteps().size(), task.doneSteps(),
                task.nextStep().map(step -> CurrentStep.of(step, shelfScanRequired.test(step))).orElse(null));
    }

    /**
     * @param shelfHeight       высота полки над полом, м: ярус должен читаться однозначно
     * @param slot              отделение тележки, куда класть
     * @param shelfScanRequired товар лежит в нескольких ячейках зоны, и по одному скану товара не
     *                          понять, откуда он взят: нужен ещё скан полки (FR-M10-04a)
     */
    public record CurrentStep(long id, int sequence, String location, Integer row, Integer section, Integer level,
            Integer position, Double shelfHeight, String article, String skuName, String uom, int quantity, int slot,
            String order, boolean shelfScanRequired) {

        static CurrentStep of(TaskStep s, boolean shelfScanRequired) {
            Location l = s.getLocation();
            return new CurrentStep(s.getId(), s.getSequence(), l.getCode(), l.getRowNo(), l.getSectionNo(),
                    l.getLevelNo(), l.getPositionNo(), l.getZ(), s.getSku().getArticle(), s.getSku().getName(),
                    s.getSku().getUom(), s.getQuantityRequired(), s.getContainerSlot(),
                    s.getOrderLine().getOrder().getNumber(), shelfScanRequired);
        }
    }
}
