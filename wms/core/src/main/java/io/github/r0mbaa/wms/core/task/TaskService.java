package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.allocation.Allocation;
import io.github.r0mbaa.wms.core.allocation.AllocationService;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.inventory.StockRepository;
import io.github.r0mbaa.wms.core.order.CustomerOrder;
import io.github.r0mbaa.wms.core.order.OrderCancelled;
import io.github.r0mbaa.wms.core.order.OrderRepository;
import io.github.r0mbaa.wms.core.order.OrderService;
import io.github.r0mbaa.wms.core.order.OrderStatus;
import io.github.r0mbaa.wms.core.order.Wave;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Задания на сборку (M9): формирование, назначение, приоритет, возврат в очередь, отмена.
 *
 * <p>Батчинг и маршрутизация — предмет исследования и живут в планировщике. Здесь только
 * ручное формирование задания диспетчером: этот режим обязан работать, даже когда планировщик
 * недоступен (NFR-R-05). Шаги ручного задания идут в порядке адресов ячеек: адрес строится от
 * начала координат склада, поэтому это обход ряд за рядом, а не оптимальный маршрут.
 */
@Service
public class TaskService {

    static final String MANUAL_BATCHING = "manual";
    static final String ADDRESS_ORDER_ROUTING = "address_order";

    private static final double EPS = 1e-9;

    private final TaskRepository tasks;
    private final ContainerRepository containers;
    private final WorkerService workers;
    private final OrderService orderService;
    private final OrderRepository orders;
    private final AllocationService allocations;
    private final StockRepository stocks;
    private final TopologyService topology;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    TaskService(TaskRepository tasks, ContainerRepository containers, WorkerService workers,
            OrderService orderService, OrderRepository orders, AllocationService allocations, StockRepository stocks,
            TopologyService topology, AuditLog audit, JdbcTemplate jdbc, Clock clock) {
        this.tasks = tasks;
        this.containers = containers;
        this.workers = workers;
        this.orderService = orderService;
        this.orders = orders;
        this.allocations = allocations;
        this.stocks = stocks;
        this.topology = topology;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Формирует задание из зарезервированных заказов в указанную тару. Каждый заказ получает своё
     * отделение тележки и целиком попадает в одно задание (FR-M7-03); число заказов, вес и объём
     * не превышают вместимости тары (FR-M7-02).
     */
    @Transactional
    public Task create(String warehouseCode, List<String> orderNumbers, String containerCode) {
        Warehouse warehouse = topology.warehouse(warehouseCode);
        if (orderNumbers == null || orderNumbers.isEmpty()) {
            throw new IllegalArgumentException("Укажите заказы, которые войдут в задание");
        }
        Set<String> numbers = new LinkedHashSet<>(orderNumbers.stream().map(String::strip).toList());
        if (numbers.size() != orderNumbers.size()) {
            throw new IllegalArgumentException("Заказ указан в задании дважды");
        }
        Container container = lockFreeContainer(warehouse, containerCode);
        if (numbers.size() > container.getSlots()) {
            throw new ConflictException("В таре " + container.getCode() + " " + container.getSlots()
                    + " отделений, а заказов " + numbers.size() + ": уберите заказы или возьмите тележку больше");
        }

        List<CustomerOrder> planned = new ArrayList<>();
        List<Pick> picks = new ArrayList<>();
        double weight = 0;
        double volume = 0;
        int slot = 0;
        for (String number : numbers) {
            CustomerOrder order = orderService.get(number);
            if (!order.getWarehouse().getId().equals(warehouse.getId())) {
                throw new IllegalArgumentException("Заказ " + number + " относится к складу "
                        + order.getWarehouse().getCode());
            }
            if (order.getStatus() != OrderStatus.ALLOCATED) {
                throw new ConflictException("Заказ " + number + " в статусе " + order.getStatus()
                        + ": в задание входят только зарезервированные заказы");
            }
            List<Allocation> active = allocations.activeOf(order);
            if (active.isEmpty()) {
                throw new ConflictException("По заказу " + number + " нет активных резервов: собирать нечего");
            }
            slot++;
            for (Allocation allocation : active) {
                picks.add(new Pick(allocation, slot));
                weight += allocation.getQuantity() * allocation.getSku().getWeightKg();
                volume += allocation.getQuantity() * allocation.getSku().getVolumeM3();
            }
            planned.add(order);
        }
        if (weight > container.getMaxWeightKg() + EPS) {
            throw new ConflictException(String.format(Locale.ROOT,
                    "Заказы весят %.1f кг, а тара %s выдерживает %.1f кг: уберите заказ из задания",
                    weight, container.getCode(), container.getMaxWeightKg()));
        }
        if (volume > container.getMaxVolumeM3() + EPS) {
            throw new ConflictException(String.format(Locale.ROOT,
                    "Заказы занимают %.3f м³, а в тару %s помещается %.3f м³: уберите заказ из задания",
                    volume, container.getCode(), container.getMaxVolumeM3()));
        }

        Instant now = clock.instant();
        Batch batch = new Batch(warehouse, commonWave(planned), container, MANUAL_BATCHING, ADDRESS_ORDER_ROUTING,
                volume, weight, now);
        for (int i = 0; i < planned.size(); i++) {
            batch.addOrder(planned.get(i), i + 1);
        }
        long sequence = Objects.requireNonNull(jdbc.queryForObject("select nextval('task_number_seq')", Long.class));
        Task task = new Task(String.format(Locale.ROOT, "TK-%06d", sequence), warehouse, batch,
                planned.stream().mapToInt(CustomerOrder::getPriority).max().orElseThrow(),
                planned.stream().map(CustomerOrder::getDeadlineAt).filter(Objects::nonNull).min(Comparator.naturalOrder())
                        .orElse(null),
                now);
        picks.stream()
                .sorted(Comparator.comparing((Pick p) -> p.allocation().getLocation().getCode()).thenComparing(Pick::slot))
                .forEach(p -> task.newStep(p.allocation(), p.slot()));
        tasks.save(task);

        for (CustomerOrder order : planned) {
            order.transition(OrderStatus.PLANNED, CustomerOrder.from(OrderStatus.ALLOCATED));
            allocations.pin(order);
        }
        audit.record("TASK_CREATED", "TASK", task.getNumber(), null, Map.of("orders", List.copyOf(numbers),
                "container", container.getCode(), "steps", task.getSteps().size()));
        return task;
    }

    /** Ручное назначение и переназначение (FR-M9-05). Начатое задание передаётся с прогрессом. */
    @Transactional
    public Task assign(String number, String workerCode) {
        Task task = get(number);
        Worker worker = workers.get(workerCode);
        if (!worker.getWarehouse().getId().equals(task.getWarehouse().getId())) {
            throw new IllegalArgumentException("Сборщик " + worker.getCode() + " работает на другом складе");
        }
        if (!worker.isOnShift()) {
            throw new ConflictException("Сборщик " + worker.getCode() + " не на смене: назначьте задание другому");
        }
        String before = task.getWorker() == null ? null : task.getWorker().getCode();
        if (task.getWorker() != null && task.getStatus() == TaskStatus.IN_PROGRESS) {
            task.getWorker().setStatus(WorkerStatus.AVAILABLE);
        }
        task.assign(worker, clock.instant());
        if (task.getStatus() == TaskStatus.IN_PROGRESS) {
            worker.setStatus(WorkerStatus.BUSY);
        }
        audit.record("TASK_ASSIGNED", "TASK", task.getNumber(), before, worker.getCode());
        return task;
    }

    /** Возврат задания в очередь с сохранением пройденных шагов (FR-M9-06). */
    @Transactional
    public Task unassign(String number) {
        Task task = get(number);
        Worker worker = task.getWorker();
        task.unassign();
        if (worker != null && worker.getStatus() == WorkerStatus.BUSY) {
            worker.setStatus(WorkerStatus.AVAILABLE);
        }
        audit.record("TASK_UNASSIGNED", "TASK", task.getNumber(), worker == null ? null : worker.getCode(), null);
        return task;
    }

    @Transactional
    public Task setPriority(String number, int priority) {
        if (priority < CustomerOrder.MIN_PRIORITY || priority > CustomerOrder.MAX_PRIORITY) {
            throw new IllegalArgumentException("Приоритет должен быть от " + CustomerOrder.MIN_PRIORITY + " до "
                    + CustomerOrder.MAX_PRIORITY);
        }
        Task task = get(number);
        int before = task.getPriority();
        task.setPriority(priority);
        audit.record("TASK_PRIORITY_CHANGED", "TASK", task.getNumber(), before, priority);
        return task;
    }

    /**
     * Расформировывает не начатое задание: заказы возвращаются в {@code ALLOCATED} с прежними
     * резервами, срок которых отсчитывается заново, а тара освобождается.
     */
    @Transactional
    public Task cancel(String number) {
        Task task = get(number);
        Instant now = clock.instant();
        task.cancel(now);
        task.getSteps().stream().filter(s -> !s.getStatus().isDone()).forEach(s -> s.cancel(now));
        for (Batch.BatchOrder entry : task.getBatch().getOrders()) {
            CustomerOrder order = entry.getOrder();
            if (order.getStatus() == OrderStatus.PLANNED) {
                order.transition(OrderStatus.ALLOCATED, CustomerOrder.from(OrderStatus.PLANNED));
                allocations.unpin(order);
            }
        }
        task.getBatch().clearOrders();
        audit.record("TASK_CANCELLED", "TASK", task.getNumber(), null, null);
        return task;
    }

    /** Отмена заказа до начала сборки убирает его шаги из не начатых заданий (FR-M5-07). */
    @EventListener
    void onOrderCancelled(OrderCancelled event) {
        CustomerOrder order = orders.findById(event.orderId()).orElseThrow();
        Instant now = clock.instant();
        Set<Task> affected = new HashSet<>();
        for (TaskStep step : tasks.stepsOf(order, EnumSet.of(TaskStatus.QUEUED, TaskStatus.ASSIGNED))) {
            if (!step.getStatus().isDone()) {
                step.cancel(now);
                affected.add(step.getTask());
            }
        }
        affected.stream().filter(t -> t.nextStep().isEmpty()).forEach(t -> t.cancel(now));
    }

    @Transactional(readOnly = true)
    public Task get(String number) {
        return tasks.findByNumber(number.strip())
                .orElseThrow(() -> new NotFoundException("Задание " + number + " не найдено"));
    }

    @Transactional(readOnly = true)
    public TaskViews.TaskView view(String number) {
        return TaskViews.TaskView.from(get(number));
    }

    /** Монитор диспетчера (FR-M9-07): активные задания или задания с указанным статусом. */
    @Transactional(readOnly = true)
    public List<TaskViews.TaskSummary> list(String warehouseCode, TaskStatus status) {
        Set<TaskStatus> statuses = status == null ? TaskStatus.ACTIVE : EnumSet.of(status);
        return tasks.findByStatuses(topology.warehouse(warehouseCode), statuses).stream()
                .map(TaskViews.TaskSummary::from)
                .toList();
    }

    private Container lockFreeContainer(Warehouse warehouse, String containerCode) {
        Container container = containers.lockByCode(ContainerService.codeOf(containerCode))
                .orElseThrow(() -> new NotFoundException("Тара " + containerCode + " не найдена"));
        if (!container.getWarehouse().getId().equals(warehouse.getId())) {
            throw new IllegalArgumentException("Тара " + container.getCode() + " относится к складу "
                    + container.getWarehouse().getCode());
        }
        if (!container.isActive()) {
            throw new ConflictException("Тара " + container.getCode() + " выведена из работы: возьмите другую");
        }
        if (tasks.existsByBatchContainerAndStatusIn(container, TaskStatus.ACTIVE)) {
            throw new ConflictException("Тара " + container.getCode() + " уже занята другим заданием: возьмите другую");
        }
        if (!stocks.findPositiveAt(container.getLocation()).isEmpty()) {
            throw new ConflictException("В таре " + container.getCode()
                    + " лежит товар прошлой сборки: сначала разложите его по заказам в зоне отгрузки");
        }
        return container;
    }

    private static Wave commonWave(List<CustomerOrder> orders) {
        Wave first = orders.getFirst().getWave();
        boolean same = first != null && orders.stream().allMatch(o -> o.getWave() != null
                && o.getWave().getId().equals(first.getId()));
        return same ? first : null;
    }

    private record Pick(Allocation allocation, int slot) {
    }
}
