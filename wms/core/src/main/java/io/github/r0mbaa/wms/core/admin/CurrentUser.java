package io.github.r0mbaa.wms.core.admin;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Имя того, кто выполняет операцию: для журнала движений и аудита. */
@Component
public class CurrentUser {

    /** Операции без пользователя: запуск приложения, фоновые задачи по расписанию. */
    public static final String SYSTEM = "system";

    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
