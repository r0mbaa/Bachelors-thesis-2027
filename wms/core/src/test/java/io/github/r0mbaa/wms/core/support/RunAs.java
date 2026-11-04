package io.github.r0mbaa.wms.core.support;

import io.github.r0mbaa.wms.core.admin.Role;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Вызов сервиса от имени пользователя, минуя HTTP: для сценариев, где важен сам пользователь
 * (сборщик на терминале), и для конкурентных тестов, где у каждого потока свой пользователь.
 *
 * <p>Создаётся отдельный контекст, а прежний восстанавливается: объект контекста теста
 * разделяет и {@code @WithMockUser}, и его нельзя менять на месте.
 */
public final class RunAs {

    private RunAs() {
    }

    public static <T> T as(String username, Role role, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
