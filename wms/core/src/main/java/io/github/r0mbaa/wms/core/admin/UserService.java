package io.github.r0mbaa.wms.core.admin;

import io.github.r0mbaa.wms.core.common.ConflictException;
import io.github.r0mbaa.wms.core.common.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Учётные записи и роли (FR-M13-01). Каждое изменение пишется в журнал аудита (FR-M13-02). */
@Service
public class UserService {

    static final String AUDIT_ENTITY = "USER";

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,32}");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    UserService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            AuditLog audit, CurrentUser currentUser, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAllByOrderByUsername();
    }

    @Transactional(readOnly = true)
    public AppUser get(String username) {
        return users.findByUsername(normalize(username))
                .orElseThrow(() -> new NotFoundException("Пользователь '" + username + "' не найден"));
    }

    @Transactional
    public AppUser create(String username, String fullName, String password, Set<Role> roles) {
        String name = normalize(username);
        if (!USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Имя пользователя '" + username
                    + "' недопустимо: 3–32 символа, латинские буквы, цифры, точка, дефис или подчёркивание");
        }
        if (users.existsByUsername(name)) {
            throw new ConflictException("Пользователь '" + name + "' уже существует: выберите другое имя");
        }
        requireRoles(roles);
        AppUser user = users.save(new AppUser(name, encode(password), fullName.strip(), roles, clock.instant()));
        audit.record("USER_CREATED", AUDIT_ENTITY, name, null,
                Map.of("fullName", user.getFullName(), "roles", sorted(roles)));
        return user;
    }

    @Transactional
    public AppUser setRoles(String username, Set<Role> roles) {
        AppUser user = get(username);
        requireRoles(roles);
        if (isSelf(user) && !roles.contains(Role.SYSTEM_ADMIN)) {
            throw new ConflictException("Нельзя снять роль SYSTEM_ADMIN с самого себя: это сделает другой администратор");
        }
        List<Role> before = sorted(user.getRoles());
        user.setRoles(roles);
        audit.record("USER_ROLES_CHANGED", AUDIT_ENTITY, user.getUsername(), before, sorted(roles));
        return user;
    }

    @Transactional
    public AppUser setEnabled(String username, boolean enabled) {
        AppUser user = get(username);
        if (isSelf(user) && !enabled) {
            throw new ConflictException("Нельзя заблокировать самого себя: это сделает другой администратор");
        }
        boolean before = user.isEnabled();
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokens.revokeAll(user, clock.instant());
        }
        audit.record(enabled ? "USER_ENABLED" : "USER_DISABLED", AUDIT_ENTITY, user.getUsername(), before, enabled);
        return user;
    }

    /** Смена пароля завершает все сессии пользователя. Сами пароли в аудит не попадают. */
    @Transactional
    public void setPassword(String username, String password) {
        AppUser user = get(username);
        user.setPasswordHash(encode(password));
        refreshTokens.revokeAll(user, clock.instant());
        audit.record("USER_PASSWORD_CHANGED", AUDIT_ENTITY, user.getUsername(), null, null);
    }

    private String encode(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Пароль должен быть не короче " + MIN_PASSWORD_LENGTH + " символов");
        }
        return passwordEncoder.encode(password);
    }

    private boolean isSelf(AppUser user) {
        return user.getUsername().equals(currentUser.username());
    }

    private static void requireRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Назначьте пользователю хотя бы одну роль");
        }
    }

    private static List<Role> sorted(Set<Role> roles) {
        return roles.stream().sorted().toList();
    }
}
