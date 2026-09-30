package io.github.r0mbaa.wms.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.r0mbaa.wms.core.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@IntegrationTest
class CoreApplicationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void startsAgainstMigratedDatabaseAndReportsHealthy() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }
}
