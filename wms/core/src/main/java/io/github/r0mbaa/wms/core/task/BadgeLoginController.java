package io.github.r0mbaa.wms.core.task;

import io.github.r0mbaa.wms.core.admin.AuthService;
import io.github.r0mbaa.wms.core.admin.AuthService.TokenPair;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Вход сборщика с терминала сканом бейджа и PIN (FR-M10-02): набирать логин и пароль в
 * перчатках на телефоне неудобно (NFR-U-03). Выдаёт те же токены, что обычный вход.
 */
@RestController
class BadgeLoginController {

    private final WorkerService workers;
    private final AuthService auth;

    BadgeLoginController(WorkerService workers, AuthService auth) {
        this.workers = workers;
        this.auth = auth;
    }

    @PostMapping("/api/v1/auth/badge")
    TokenPair login(@Valid @RequestBody BadgeRequest request) {
        return workers.authenticate(request.badge(), request.pin())
                .map(auth::issueTokens)
                .orElseThrow(() -> new BadCredentialsException("Неверный бейдж или PIN"));
    }

    /** @param badge скан QR бейджа {@code WRK:…} или его код */
    record BadgeRequest(@NotBlank String badge, @NotBlank String pin) {
    }
}
