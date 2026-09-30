package io.github.r0mbaa.wms.core.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Очищает все прикладные таблицы перед каждым тестом. Откат транзакции теста здесь не годится:
 * проверки блокировок и конкурентного резервирования требуют настоящих коммитов.
 *
 * <p>TRUNCATE не вызывает строковые триггеры, поэтому запрет на изменение журнала движений
 * очистке не мешает.
 */
class TruncateTablesExtension implements BeforeEachCallback {

    private static final String TABLES = """
            select string_agg(format('%I.%I', schemaname, tablename), ', ')
            from pg_tables
            where schemaname = 'public' and tablename <> 'flyway_schema_history'
            """;

    @Override
    public void beforeEach(ExtensionContext context) {
        JdbcTemplate jdbc = SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
        String tables = jdbc.queryForObject(TABLES, String.class);
        if (tables != null) {
            jdbc.execute("truncate " + tables + " restart identity cascade");
        }
    }
}
