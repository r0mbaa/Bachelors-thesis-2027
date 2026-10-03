package io.github.r0mbaa.wms.core.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.support.IntegrationTest;
import io.github.r0mbaa.wms.core.support.Json;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AuthApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    UserService users;

    @Autowired
    AppUserRepository userRepository;

    @BeforeEach
    void createUser() {
        users.create("Ivanov", "Иванов И. И.", "secret-pass", Set.of(Role.RECEIVER, Role.SHIPPER));
    }

    @Test
    void storesOnlyBcryptHashOfPassword() {
        assertThat(userRepository.findByUsername("ivanov").orElseThrow().getPasswordHash())
                .startsWith("{bcrypt}")
                .doesNotContain("secret-pass");
    }

    @Test
    void loginIssuesAccessTokenAcceptedByApi() {
        MvcTestResult login = login("IVANOV ", "secret-pass");
        assertThat(login).hasStatusOk()
                .bodyJson().extractingPath("$.roles").asArray().containsExactly("RECEIVER", "SHIPPER");

        String accessToken = Json.read(login, "$.accessToken");
        assertThat(mvc.get().uri("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .hasStatusOk()
                .bodyJson().extractingPath("$.username").isEqualTo("ivanov");
    }

    @Test
    void wrongPasswordAndUnknownUserGetSameAnswer() {
        assertThat(login("ivanov", "wrong-pass")).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Неверное имя пользователя или пароль");
        assertThat(login("petrov", "secret-pass")).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Неверное имя пользователя или пароль");
    }

    @Test
    void requestWithoutTokenOrWithForgedTokenIsRejected() {
        assertThat(mvc.get().uri("/api/v1/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void disabledUserCannotLogIn() {
        users.setEnabled("ivanov", false);

        assertThat(login("ivanov", "secret-pass")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshTokenIsSingleUseAndItsReuseEndsAllSessions() {
        String first = Json.read(login("ivanov", "secret-pass"), "$.refreshToken");

        MvcTestResult rotated = refresh(first);
        assertThat(rotated).hasStatusOk();
        String second = Json.read(rotated, "$.refreshToken");
        assertThat(second).isNotEqualTo(first);

        // Старый токен предъявлен повторно: так выглядит украденная копия.
        assertThat(refresh(first)).hasStatus(HttpStatus.UNAUTHORIZED);
        // Поэтому законный владелец тоже теряет сессию и входит заново.
        assertThat(refresh(second)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesRefreshToken() {
        MvcTestResult login = login("ivanov", "secret-pass");
        String accessToken = Json.read(login, "$.accessToken");
        String refreshToken = Json.read(login, "$.refreshToken");

        assertThat(mvc.post().uri("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(refresh(refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordChangeEndsSessions() {
        String refreshToken = Json.read(login("ivanov", "secret-pass"), "$.refreshToken");

        users.setPassword("ivanov", "new-secret-pass");

        assertThat(refresh(refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login("ivanov", "new-secret-pass")).hasStatusOk();
    }

    private MvcTestResult login(String username, String password) {
        return mvc.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}")
                .exchange();
    }

    private MvcTestResult refresh(String refreshToken) {
        return mvc.post().uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + refreshToken + "\"}")
                .exchange();
    }
}
