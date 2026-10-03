package io.github.r0mbaa.wms.core.admin;

import java.time.Instant;
import java.util.List;

public record UserView(String username, String fullName, List<Role> roles, boolean enabled, Instant createdAt) {

    static UserView from(AppUser user) {
        return new UserView(user.getUsername(), user.getFullName(), user.getRoles().stream().sorted().toList(),
                user.isEnabled(), user.getCreatedAt());
    }
}
