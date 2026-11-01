package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.admin.AppUser;
import io.github.r0mbaa.wms.core.admin.AuditLog;
import io.github.r0mbaa.wms.core.admin.Role;
import io.github.r0mbaa.wms.core.admin.UserService;
import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import io.github.r0mbaa.wms.core.topology.TopologyService;
import io.github.r0mbaa.wms.core.topology.Warehouse;
import io.github.r0mbaa.wms.shared.marking.QrPayload;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Реестр сборщиков (FR-M9-01) и их PIN для входа с терминала (FR-M10-02). */
@Service
public class WorkerService {

    private static final Pattern PIN = Pattern.compile("\\d{4,8}");

    private final WorkerRepository workers;
    private final UserService users;
    private final TopologyService topology;
    private final PasswordEncoder passwordEncoder;
    private final AuditLog audit;

    WorkerService(WorkerRepository workers, UserService users, TopologyService topology,
            PasswordEncoder passwordEncoder, AuditLog audit) {
        this.workers = workers;
        this.users = users;
        this.topology = topology;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    /**
     * Регистрирует пользователя с ролью сборщика как исполнителя заданий на складе.
     *
     * @param code код бейджа, он же содержимое QR {@code WRK:…}
     */
    @Transactional
    public Worker register(String warehouseCode, String username, String code, String pin) {
        Warehouse warehouse = topology.warehouse(warehouseCode);
        AppUser user = users.get(username);
        if (!user.getRoles().contains(Role.PICKER)) {
            throw new ConflictException("У пользователя " + user.getUsername()
                    + " нет роли PICKER: сначала назначьте её, затем регистрируйте сборщика");
        }
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (workers.existsByCode(normalized)) {
            throw new ConflictException("Бейдж " + normalized + " уже выдан другому сборщику");
        }
        if (workers.existsByUserUsername(user.getUsername())) {
            throw new ConflictException("Пользователь " + user.getUsername() + " уже зарегистрирован как сборщик");
        }
        Worker worker = workers.save(new Worker(warehouse, user, normalized, encodePin(pin)));
        audit.record("WORKER_REGISTERED", "WORKER", normalized, null, Map.of("username", user.getUsername()));
        return worker;
    }

    @Transactional
    public void setPin(String code, String pin) {
        Worker worker = get(code);
        worker.setPinHash(encodePin(pin));
        audit.record("WORKER_PIN_CHANGED", "WORKER", worker.getCode(), null, null);
    }

    @Transactional(readOnly = true)
    public List<Worker> list(String warehouseCode) {
        return workers.findByWarehouseOrderByCode(topology.warehouse(warehouseCode));
    }

    @Transactional(readOnly = true)
    public Worker get(String scanned) {
        return workers.findByCode(codeOf(scanned))
                .orElseThrow(() -> new NotFoundException("Сборщик с бейджем " + scanned + " не найден"));
    }

    @Transactional(readOnly = true)
    public Worker ofUser(String username) {
        return workers.findByUserUsername(username)
                .orElseThrow(() -> new ConflictException("Пользователь " + username
                        + " не зарегистрирован как сборщик: обратитесь к администратору склада"));
    }

    /**
     * Проверяет бейдж и PIN (FR-M10-02).
     *
     * @return имя пользователя сборщика, если PIN верен
     */
    @Transactional(readOnly = true)
    public Optional<String> authenticate(String badge, String pin) {
        return workers.findByCode(codeOf(badge))
                .filter(worker -> pin != null && passwordEncoder.matches(pin, worker.getPinHash()))
                .map(worker -> worker.getUser().getUsername());
    }

    private String encodePin(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new IllegalArgumentException("PIN должен состоять из 4–8 цифр");
        }
        return passwordEncoder.encode(pin);
    }

    static String codeOf(String scanned) {
        String code = scanned == null ? "" : scanned.strip();
        if (code.regionMatches(true, 0, "WRK:", 0, 4)) {
            code = ((QrPayload.Worker) QrPayload.parse(code)).code();
        }
        return code.toUpperCase(Locale.ROOT);
    }
}
