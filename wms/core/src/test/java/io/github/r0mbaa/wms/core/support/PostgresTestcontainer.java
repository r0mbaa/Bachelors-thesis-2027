package io.github.r0mbaa.wms.core.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Настоящий PostgreSQL той же версии, что в docker-compose.yml. Инварианты учёта держатся на
 * CHECK-ограничениях, триггерах и блокировках строк (§12.2), и встраиваемая БД их не проверит.
 *
 * <p>Контейнер — бин контекста, поэтому все тестовые классы с одинаковой конфигурацией делят
 * один контейнер через кэш контекстов Spring.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
