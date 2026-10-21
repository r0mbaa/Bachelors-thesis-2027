package io.github.r0mbaa.wms.core.order;

import io.github.r0mbaa.wms.core.admin.CurrentUser;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Формирование волн (FR-M5-05). Срочные заказы в волны не попадают (FR-M5-08). */
@Service
public class WaveService {

    public static final int DEFAULT_MAX_ORDERS = 50;

    private final WaveRepository waves;
    private final OrderRepository orders;
    private final TopologyService topology;
    private final CurrentUser currentUser;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    WaveService(WaveRepository waves, OrderRepository orders, TopologyService topology, CurrentUser currentUser,
            JsonMapper json, JdbcTemplate jdbc, Clock clock) {
        this.waves = waves;
        this.orders = orders;
        this.topology = topology;
        this.currentUser = currentUser;
        this.json = json;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Отбирает в волну новые заказы по критериям: сначала с ближайшим дедлайном, затем с
     * большим приоритетом. Критерии сохраняются вместе с волной.
     */
    @Transactional
    public Wave form(String warehouseCode, WaveCriteria criteria) {
        Warehouse warehouse = topology.warehouse(warehouseCode);
        int maxOrders = criteria.maxOrders() == null ? DEFAULT_MAX_ORDERS : criteria.maxOrders();
        int minPriority = criteria.minPriority() == null ? CustomerOrder.MIN_PRIORITY : criteria.minPriority();
        if (maxOrders < 1) {
            throw new IllegalArgumentException("Максимальный размер волны должен быть положительным");
        }
        boolean anyDeadline = criteria.deadlineBefore() == null;
        List<CustomerOrder> selected = orders.lockWaveCandidates(warehouse, OrderStatus.NEW, anyDeadline,
                anyDeadline ? Instant.EPOCH : criteria.deadlineBefore(), blankToNull(criteria.carrier()),
                blankToNull(criteria.direction()), minPriority, PageRequest.of(0, maxOrders));
        if (selected.isEmpty()) {
            throw new ConflictException("Нет новых заказов вне волн, подходящих под критерии: ослабьте фильтры "
                    + "или дождитесь поступления заказов");
        }
        long sequence = Objects.requireNonNull(jdbc.queryForObject("select nextval('wave_number_seq')", Long.class));
        Wave wave = waves.save(new Wave(warehouse, String.format(Locale.ROOT, "W-%06d", sequence),
                json.writeValueAsString(criteria), currentUser.username(), clock.instant()));
        selected.forEach(order -> order.joinWave(wave));
        return wave;
    }

    @Transactional(readOnly = true)
    public Wave get(String number) {
        return waves.findByNumber(number.strip())
                .orElseThrow(() -> new NotFoundException("Волна " + number + " не найдена"));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrder> ordersOf(Wave wave) {
        return orders.findInWave(wave);
    }

    @Transactional(readOnly = true)
    public List<Wave> list(String warehouseCode) {
        return waves.findByWarehouseOrderByCreatedAtDesc(topology.warehouse(warehouseCode));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Критерии волны; незаданный критерий не ограничивает.
     *
     * @param deadlineBefore дедлайн заказа не позже
     * @param minPriority    приоритет не ниже
     * @param maxOrders      не больше заказов в волне, по умолчанию {@value #DEFAULT_MAX_ORDERS}
     */
    public record WaveCriteria(Instant deadlineBefore, String carrier, String direction, Integer minPriority,
            Integer maxOrders) {
    }
}
