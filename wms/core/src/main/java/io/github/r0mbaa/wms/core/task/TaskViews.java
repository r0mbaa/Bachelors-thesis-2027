package io.github.r0mbaa.wms.core.task;

import java.time.Instant;
import java.util.List;

/** Представления заданий. Собираются внутри транзакции: шаги и их связи загружаются лениво. */
public final class TaskViews {

    private TaskViews() {
    }

    /**
     * @param algorithmBatching чем построен батч: {@code manual} — сформирован диспетчером вручную
     * @param algorithmRouting  чем построен маршрут: {@code address_order} — порядок адресов ячеек
     */
    public record TaskSummary(String number, TaskStatus status, String worker, int priority, Instant deadlineAt,
            String container, List<String> orders, int steps, long stepsDone, String algorithmBatching,
            String algorithmRouting, Instant createdAt, Instant startedAt, Instant finishedAt) {

        public static TaskSummary from(Task t) {
            Batch batch = t.getBatch();
            return new TaskSummary(t.getNumber(), t.getStatus(), t.getWorker() == null ? null : t.getWorker().getCode(),
                    t.getPriority(), t.getDeadlineAt(), batch.getContainer().getCode(),
                    batch.getOrders().stream().map(o -> o.getOrder().getNumber()).toList(),
                    t.getSteps().size(), t.doneSteps(), batch.getAlgorithmBatching(), batch.getAlgorithmRouting(),
                    t.getCreatedAt(), t.getStartedAt(), t.getFinishedAt());
        }
    }

    public record TaskView(TaskSummary summary, double totalWeightKg, double totalVolumeM3, List<StepView> steps) {

        public static TaskView from(Task t) {
            return new TaskView(TaskSummary.from(t), t.getBatch().getTotalWeightKg(), t.getBatch().getTotalVolumeM3(),
                    t.getSteps().stream().map(StepView::from).toList());
        }
    }

    /**
     * @param level ярус ячейки: на терминале показывается крупно (FR-M10-03)
     * @param slot  отделение тележки, куда кладётся отобранное
     */
    public record StepView(long id, int sequence, String location, Integer level, String article, String skuName,
            int required, int picked, int slot, String order, StepStatus status, PickException exception,
            String comment) {

        public static StepView from(TaskStep s) {
            return new StepView(s.getId(), s.getSequence(), s.getLocation().getCode(), s.getLocation().getLevelNo(),
                    s.getSku().getArticle(), s.getSku().getName(), s.getQuantityRequired(), s.getQuantityPicked(),
                    s.getContainerSlot(), s.getOrderLine().getOrder().getNumber(), s.getStatus(), s.getExceptionType(),
                    s.getComment());
        }
    }
}
