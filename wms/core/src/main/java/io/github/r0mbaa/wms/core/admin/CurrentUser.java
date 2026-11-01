package io.github.r0mbaa.wms.core.admin;

import java.util.Collection;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Кто выполняет операцию: для журнала движений, аудита и проверок, зависящих от роли. */
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

    public boolean hasAnyRole(Collection<Role> roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> roles.stream().anyMatch(role -> authority.equals("ROLE_" + role.name())));
    }
}
