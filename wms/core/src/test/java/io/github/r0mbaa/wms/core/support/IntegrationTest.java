package io.github.r0mbaa.wms.core.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Интеграционный тест {@code core}: полный контекст, MockMvc и PostgreSQL в Testcontainers.
 * Все тесты с этой аннотацией делят один контекст и один контейнер, данные очищаются перед
 * каждым тестом.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@ExtendWith(TruncateTablesExtension.class)
public @interface IntegrationTest {
}
