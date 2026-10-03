package io.github.r0mbaa.wms.core.admin;

import io.github.r0mbaa.wms.core.admin.AuthService.TokenPair;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService auth;
    private final UserService users;
    private final CurrentUser currentUser;

    AuthController(AuthService auth, UserService users, CurrentUser currentUser) {
        this.auth = auth;
        this.users = users;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    TokenPair login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }

    @GetMapping("/me")
    UserView me() {
        return UserView.from(users.get(currentUser.username()));
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }
}
