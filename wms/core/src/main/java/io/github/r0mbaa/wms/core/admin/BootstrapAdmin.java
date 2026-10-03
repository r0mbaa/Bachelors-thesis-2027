package io.github.r0mbaa.wms.core.admin;

import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Первый вход в пустую систему. Пароль по умолчанию не зашит: администратор создаётся, только
 * если пароль передан через {@code WMS_ADMIN_PASSWORD}.
 */
@Component
class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final AppUserRepository users;
    private final UserService userService;
    private final SecurityProperties properties;

    BootstrapAdmin(AppUserRepository users, UserService userService, SecurityProperties properties) {
        this.users = users;
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        SecurityProperties.Bootstrap bootstrap = properties.bootstrap();
        if (bootstrap.password() == null || bootstrap.password().isBlank()) {
            log.warn("В системе нет пользователей. Задайте WMS_ADMIN_PASSWORD и перезапустите, "
                    + "чтобы создать администратора '{}'", bootstrap.username());
            return;
        }
        // Все роли, кроме сборщика: для сокращённого варианта §15.4 хватает двух пользователей.
        userService.create(bootstrap.username(), "Администратор", bootstrap.password(),
                EnumSet.complementOf(EnumSet.of(Role.PICKER)));
        log.info("Создан администратор '{}'", bootstrap.username());
    }
}
