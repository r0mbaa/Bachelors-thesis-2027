package io.github.r0mbaa.wms.core.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    List<UserView> list() {
        return users.list().stream().map(UserView::from).toList();
    }

    @GetMapping("/{username}")
    UserView get(@PathVariable String username) {
        return UserView.from(users.get(username));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserView create(@Valid @RequestBody CreateUserRequest request) {
        return UserView.from(users.create(request.username(), request.fullName(), request.password(), request.roles()));
    }

    @PutMapping("/{username}/roles")
    UserView setRoles(@PathVariable String username, @Valid @RequestBody RolesRequest request) {
        return UserView.from(users.setRoles(username, request.roles()));
    }

    @PutMapping("/{username}/enabled")
    UserView setEnabled(@PathVariable String username, @Valid @RequestBody EnabledRequest request) {
        return UserView.from(users.setEnabled(username, request.enabled()));
    }

    @PutMapping("/{username}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setPassword(@PathVariable String username, @Valid @RequestBody PasswordRequest request) {
        users.setPassword(username, request.password());
    }

    record CreateUserRequest(
            @NotBlank String username, @NotBlank String fullName, @NotBlank String password, @NotEmpty Set<Role> roles) {
    }

    record RolesRequest(@NotEmpty Set<Role> roles) {
    }

    record EnabledRequest(@NotNull Boolean enabled) {
    }

    record PasswordRequest(@NotBlank String password) {
    }
}
