package io.github.r0mbaa.wms.core.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class OpenApiTest {

    @Autowired
    MockMvcTester mvc;

    /** NFR-M-07: описание API строится автоматически и доступно без входа. */
    @Test
    void apiDescriptionCoversControllersAndDeclaresBearerAuth() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.openapi", v -> v.assertThat().asString().startsWith("3.1"))
                .hasPathSatisfying("$.paths['/api/v1/terminal/steps/{stepId}/confirm']", v -> v.assertThat().isNotNull())
                .hasPathSatisfying("$.components.securitySchemes.bearer-jwt.scheme",
                        v -> v.assertThat().isEqualTo("bearer"));
    }
}
