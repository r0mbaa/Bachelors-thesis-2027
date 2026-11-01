package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.admin.CurrentUser;
import io.github.r0mbaa.wms.core.allocation.AllocationService;
import io.github.r0mbaa.wms.core.catalog.CatalogService;
import io.github.r0mbaa.wms.core.catalog.Sku;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.inventory.DocumentRef;
import io.github.r0mbaa.wms.core.inventory.MovementType;
import io.github.r0mbaa.wms.core.inventory.StockLedger;
import io.github.r0mbaa.wms.core.inventory.StockLedger.Posting;
import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.topology.Location;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Серверная часть терминала сборщика (M10, процесс §6.3): смена, получение задания, старт по
 * скану тары, подтверждение шагов и исключения (§6.4), завершение.
 *
 * <p>Шаг подтверждается сканом только товара (FR-M10-04). Скан полки нужен лишь тогда, когда
 * товар лежит в нескольких ячейках зоны и по скану товара не понять, откуда он взят
 * (FR-M10-04a, INV-08). Подтверждение переносит товар из ячейки в тару движением {@code PICK}
 * (FR-M10-11); со склада он списывается только при отгрузке.
 *
 * <p>События подтверждения и исключения идемпотентны по ключу (FR-M10-08, NFR-R-03), а события
 * одного задания обрабатываются по очереди под блокировкой строки задания.
 */
@Service
public class TerminalService {

    private static final EnumSet<TaskStatus> HELD = EnumSet.of(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS);

    private final TaskRepository tasks;
    private final WorkerService workers;
    private final CatalogService catalog;
    private final TopologyService topology;
    private final StockLedger ledger;
    private final AllocationService allocations;
    private final CurrentUser currentUser;
    private final AuditLog audit;
    private final TerminalProperties properties;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    TerminalService(TaskRepository tasks, WorkerService workers, CatalogService catalog, TopologyService topology,
            StockLedger ledger, AllocationService allocations, CurrentUser currentUser, AuditLog audit,
            TerminalProperties properties, JdbcTemplate jdbc, Clock clock) {
        this.tasks = tasks;
        this.workers = workers;
        this.catalog = catalog;
        this.topology = topology;
        this.ledger = ledger;
        this.allocations = allocations;
        this.currentUser = currentUser;
        this.audit = audit;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public WorkerStatus startShift() {
        Worker worker = me();
        worker.setStatus(heldTask(worker).filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).isPresent()
                ? WorkerStatus.BUSY : WorkerStatus.AVAILABLE);
        return worker.getStatus();
    }

    @Transactional
    public WorkerStatus takeBreak() {
        Worker worker = me();
        if (heldTask(worker).filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).isPresent()) {
            throw new ConflictException("Сначала завершите задание или верните его диспетчеру, затем уходите на перерыв");
        }
        worker.setStatus(WorkerStatus.BREAK);
        return worker.getStatus();
    }

    /** Уход со смены возвращает задание в очередь с сохранением пройденных шагов (FR-M9-06). */
    @Transactional
    public WorkerStatus endShift() {
        Worker worker = me();
        heldTask(worker).ifPresent(task -> {
            task.unassign();
            audit.record("TASK_RETURNED_ON_SHIFT_END", "TASK", task.getNumber(), worker.getCode(), null);
        });
        worker.setStatus(WorkerStatus.OFF_SHIFT);
        return worker.getStatus();
    }

    @Transactional(readOnly = true)
    public Optional<TerminalView> current() {
        return heldTask(me()).map(this::view);
    }

    /**
     * Выдаёт сборщику задание: уже назначенное ему или первое в очереди по раннему дедлайну,
     * затем по приоритету. Это простое правило, а не исследуемая диспетчеризация (§15.4).
     *
     * @return пусто, если очередь пуста
     */
    @Transactional
    public Optional<TerminalView> claimNext() {
        Worker worker = me();
        Optional<Task> held = heldTask(worker);
        if (held.isPresent()) {
            return held.map(this::view);
        }
        if (worker.getStatus() != WorkerStatus.AVAILABLE) {
            throw new ConflictException("Сначала начните смену: задания выдаются только свободному сборщику");
        }
        List<Task> queued = tasks.lockQueued(worker.getWarehouse(), TaskStatus.QUEUED, PageRequest.of(0, 1));
        return queued.stream().findFirst().map(task -> {
            task.assign(worker, clock.instant());
            return view(task);
        });
    }

    /** Старт по скану тары (§6.3, шаг 1): сборщик подтверждает, что взял ту тележку, что в задании. */
    @Transactional
    public TerminalView start(String containerScan) {
        Worker worker = me();
        Task task = heldTask(worker).orElseThrow(() -> new ConflictException(
                "У вас нет назначенного задания: запросите следующее"));
        Container container = task.getBatch().getContainer();
        if (!ContainerService.codeOf(containerScan).equals(container.getCode())) {
            throw new ConflictException("Отсканирована тара " + ContainerService.codeOf(containerScan)
                    + ", а задание " + task.getNumber() + " собирается в " + container.getCode()
                    + ": возьмите нужную тележку");
        }
        task.start(clock.instant());
        worker.setStatus(WorkerStatus.BUSY);
        for (Batch.BatchOrder entry : task.getBatch().getOrders()) {
            CustomerOrder order = entry.getOrder();
            if (order.getStatus() == OrderStatus.PLANNED) {
                order.transition(OrderStatus.IN_PROGRESS, CustomerOrder.from(OrderStatus.PLANNED));
            }
        }
        return view(task);
    }

    /**
     * Подтверждение шага сканом товара (FR-M10-04). Количество по умолчанию — требуемое
     * (FR-M10-04c); меньше требуемого — недостача, строка закрывается частично.
     *
     * @param locationScan скан полки; обязателен, только если товар лежит в нескольких ячейках зоны
     * @param manual       код введён вручную, а не отсканирован (FR-M10-04b)
     */
    @Transactional
    public TerminalView confirm(long stepId, UUID eventKey, String skuScan, String locationScan, Integer quantity,
            boolean manual) {
        Task task = lockTaskOfStep(stepId);
        TaskStep step = step(task, stepId);
        if (!recordEvent(eventKey, task, step, "CONFIRM")) {
            return view(task);
        }
        Worker worker = requireCurrentStep(task, step);
        if (manual) {
            requireManualEntryAllowed(task, skuScan);
        }
        Sku scanned = catalog.find(skuScan).orElseThrow(() -> new NotFoundException("Товар по коду '" + skuScan
                + "' не найден: отсканируйте QR на товаре"));
        if (!scanned.getId().equals(step.getSku().getId())) {
            throw new ConflictException("Отсканирован товар " + scanned.getArticle() + ", а на этом шаге нужен "
                    + step.getSku().getArticle() + " из ячейки " + step.getLocation().getCode()
                    + ": положите товар обратно и возьмите нужный");
        }
        boolean shelfRequired = shelfScanRequired(step);
        if (locationScan != null && !locationScan.isBlank()) {
            Location scannedLocation = topology.resolveLocation(locationScan);
            if (!scannedLocation.getId().equals(step.getLocation().getId())) {
                throw new ConflictException("Отсканирована полка " + scannedLocation.getCode() + ", а товар берётся из "
                        + step.getLocation().getCode() + ": подойдите к нужной ячейке");
            }
        } else if (shelfRequired) {
            throw new ConflictException("Товар " + step.getSku().getArticle()
                    + " лежит в нескольких ячейках: отсканируйте ещё и QR полки " + step.getLocation().getCode());
        }
        int picked = quantity == null ? step.getQuantityRequired() : quantity;
        if (picked < 1 || picked > step.getQuantityRequired()) {
            throw new IllegalArgumentException("Количество должно быть от 1 до " + step.getQuantityRequired()
                    + ": больше требуемого по заданию брать нельзя, при нехватке зарегистрируйте исключение");
        }
        boolean isShort = picked < step.getQuantityRequired();
        settle(task, step, worker, picked, isShort ? PickException.INSUFFICIENT : null,
                isShort ? "Отобрано меньше требуемого" : null);
        return view(task);
    }

    /**
     * Исключение при сборке (FR-M10-06, §6.4). Строка закрывается фактически отобранным; её
     * переаллокацию в другую ячейку и перестроение остатка маршрута выполнит планировщик, а пока
     * недобранное видно в заказе и решается диспетчером.
     *
     * @param picked  сколько всё же отобрано годного
     * @param damaged сколько повреждено; списывается из ячейки (только для {@code DAMAGED})
     */
    @Transactional
    public TerminalView reportException(long stepId, UUID eventKey, PickException type, int picked, int damaged,
            String comment) {
        Task task = lockTaskOfStep(stepId);
        TaskStep step = step(task, stepId);
        if (!recordEvent(eventKey, task, step, "EXCEPTION")) {
            return view(task);
        }
        Worker worker = requireCurrentStep(task, step);
        int required = step.getQuantityRequired();
        String note = comment == null || comment.isBlank() ? null : comment.strip();
        switch (type) {
            case INSUFFICIENT -> {
                if (picked < 1 || picked >= required) {
                    throw new IllegalArgumentException("При недостаче укажите, сколько отобрано: от 1 до " + (required - 1)
                            + "; если товара нет совсем, выберите «товар отсутствует»");
                }
                settle(task, step, worker, picked, type, note);
            }
            case MISSING, LOCATION_UNAVAILABLE -> settle(task, step, worker, 0, type, note);
            case DAMAGED -> {
                if (damaged < 1 || picked < 0 || picked + damaged > required) {
                    throw new IllegalArgumentException("Укажите, сколько повреждено (не меньше 1) и сколько годного "
                            + "отобрано: вместе не больше " + required);
                }
                settle(task, step, worker, picked, type, note);
                ledger.post(new Posting(MovementType.WRITE_OFF, step.getSku(), damaged, step.getLocation(), null,
                        document(task), "Повреждено при сборке" + (note == null ? "" : ": " + note)));
            }
            case MISPLACED -> {
                settle(task, step, worker, 0, type, note);
                topology.block(step.getLocation().getCode(), "Пересорт при сборке задания " + task.getNumber()
                        + ": нужна инвентаризация ячейки");
            }
        }
        return view(task);
    }

    /** Завершение после последней точки (§6.3, шаг 5): тележка сдаётся на консолидацию. */
    @Transactional
    public TerminalView complete() {
        Worker worker = me();
        Task task = tasks.heldTaskIds(worker, HELD).stream().findFirst()
                .flatMap(tasks::lockById)
                .orElseThrow(() -> new ConflictException("У вас нет задания в работе"));
        task.complete(clock.instant());
        for (Batch.BatchOrder entry : task.getBatch().getOrders()) {
            CustomerOrder order = entry.getOrder();
            if (order.getStatus() != OrderStatus.IN_PROGRESS) {
                continue;
            }
            boolean complete = order.getLines().stream()
                    .allMatch(l -> l.getQuantityPicked() == l.getQuantityOrdered());
            order.transition(complete ? OrderStatus.PICKED : OrderStatus.PARTIALLY_PICKED,
                    CustomerOrder.from(OrderStatus.IN_PROGRESS));
        }
        worker.setStatus(WorkerStatus.AVAILABLE);
        return view(task);
    }

    private void settle(Task task, TaskStep step, Worker worker, int picked, PickException exception, String comment) {
        ledger.pick(step.getLocation(), task.getBatch().getContainer().getLocation(), step.getSku(), picked,
                step.getQuantityRequired(), document(task), comment);
        allocations.settle(step.getAllocation(), picked);
        step.getOrderLine().pick(picked);
        StepStatus result = exception == null ? StepStatus.PICKED
                : picked == 0 && (exception == PickException.LOCATION_UNAVAILABLE || exception == PickException.MISPLACED)
                        ? StepStatus.SKIPPED
                        : StepStatus.SHORT;
        step.complete(result, picked, exception, comment, clock.instant());
        worker.setCurrentLocation(step.getLocation());
    }

    /**
     * Скан полки нужен, если товар шага лежит больше чем в одной ячейке зоны (FR-M10-04a). В зоне
     * с выделенным местом так не бывает (INV-08), поэтому там хватает скана товара.
     */
    private boolean shelfScanRequired(TaskStep step) {
        if (step.getLocation().getZone() == null) {
            return false;
        }
        Integer cells = jdbc.queryForObject("""
                select count(*) from stock s join location l on l.id = s.location_id
                where l.zone_id = ? and s.sku_id = ? and s.quantity > 0
                """, Integer.class, step.getLocation().getZone().getId(), step.getSku().getId());
        return Objects.requireNonNull(cells) > 1;
    }

    /**
     * Ручной ввод кода вместо сканирования допустим только для разрешённых ролей и попадает в
     * аудит (FR-M10-04b, NFR-SEC-06).
     */
    private void requireManualEntryAllowed(Task task, String code) {
        if (!currentUser.hasAnyRole(properties.manualEntryRoles())) {
            throw new AccessDeniedException(
                    "Ручной ввод кода доступен ролям " + properties.manualEntryRoles());
        }
        audit.record("MANUAL_CODE_ENTRY", "TASK", task.getNumber(), null, Map.of("code", code));
    }

    /** @return {@code false}, если событие с этим ключом уже обработано */
    private boolean recordEvent(UUID eventKey, Task task, TaskStep step, String type) {
        return jdbc.update("""
                insert into terminal_event (event_key, task_id, step_id, type, username, received_at)
                values (?, ?, ?, ?, ?, now())
                on conflict (event_key) do nothing
                """, eventKey, task.getId(), step.getId(), type, currentUser.username()) == 1;
    }

    private Worker requireCurrentStep(Task task, TaskStep step) {
        Worker worker = me();
        if (task.getWorker() == null || !task.getWorker().getId().equals(worker.getId())) {
            throw new ConflictException("Задание " + task.getNumber() + " назначено не вам: запросите своё задание");
        }
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new ConflictException("Задание " + task.getNumber() + " не начато: отсканируйте тележку");
        }
        TaskStep next = task.nextStep().orElseThrow(() -> new ConflictException("Все шаги пройдены: завершите задание"));
        if (!next.getId().equals(step.getId())) {
            step.requirePending();
            throw new ConflictException("Сейчас шаг " + next.getSequence() + " (" + next.getLocation().getCode()
                    + "): маршрут проходится по порядку");
        }
        return worker;
    }

    private Task lockTaskOfStep(long stepId) {
        return tasks.taskIdOfStep(stepId).flatMap(tasks::lockById)
                .orElseThrow(() -> new NotFoundException("Шаг " + stepId + " не найден"));
    }

    private static TaskStep step(Task task, long stepId) {
        return task.getSteps().stream().filter(s -> s.getId() == stepId).findFirst().orElseThrow();
    }

    private Optional<Task> heldTask(Worker worker) {
        return tasks.findByWorkerAndStatusIn(worker, HELD).stream().findFirst();
    }

    private Worker me() {
        return workers.ofUser(currentUser.username());
    }

    private TerminalView view(Task task) {
        return TerminalView.of(task, this::shelfScanRequired);
    }

    private static DocumentRef document(Task task) {
        return new DocumentRef("TASK", task.getNumber());
    }
}
