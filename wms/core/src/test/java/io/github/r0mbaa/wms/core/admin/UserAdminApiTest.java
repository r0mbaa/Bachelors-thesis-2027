package io.github.r0mbaa.wms.core.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.support.IntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class UserAdminApiTest {

    private static final String NEW_PICKER = """
            {"username": "picker01", "fullName": "Петров П. П.", "password": "picker-pass", "roles": ["PICKER"]}
            """;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserService users;

    @Test
    @WithMockUser(username = "root", roles = "SYSTEM_ADMIN")
    void systemAdminCreatesUserAndChangeIsAudited() {
        assertThat(mvc.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(NEW_PICKER))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.roles").asArray().containsExactly("PICKER");

        assertThat(mvc.get().uri("/api/v1/audit").param("entityType", "USER").param("entityId", "picker01"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.items[0].action", v -> v.assertThat().isEqualTo("USER_CREATED"))
                .hasPathSatisfying("$.items[0].actor", v -> v.assertThat().isEqualTo("root"))
                .hasPathSatisfying("$.items[0].newValue.roles", v -> v.assertThat().asArray().containsExactly("PICKER"));
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_ADMIN")
    void otherRolesCannotManageUsers() {
        assertThat(mvc.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(NEW_PICKER))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.title").isEqualTo("Недостаточно прав");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void duplicateUsernameIsConflict() {
        users.create("picker01", "Петров П. П.", "picker-pass", Set.of(Role.PICKER));

        assertThat(mvc.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(NEW_PICKER))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("уже существует");
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void invalidRequestListsFieldsToFix() {
        assertThat(mvc.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"picker01\", \"fullName\": \"\", \"password\": \"picker-pass\", \"roles\": []}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder("fullName", "roles");
    }

    @Test
    @WithMockUser(username = "root", roles = "SYSTEM_ADMIN")
    void adminCannotLockThemselvesOut() {
        users.create("root", "Администратор", "root-password", Set.of(Role.SYSTEM_ADMIN));

        assertThat(mvc.put().uri("/api/v1/users/root/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\": [\"WAREHOUSE_ADMIN\"]}"))
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.put().uri("/api/v1/users/root/enabled").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
                .hasStatus(HttpStatus.CONFLICT);
    }
}
