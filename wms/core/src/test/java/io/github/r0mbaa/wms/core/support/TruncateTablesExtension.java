package io.github.r0mbaa.wms.core.support;

import java.util.List;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Очищает все прикладные таблицы и перезапускает последовательности номеров документов перед
 * каждым тестом. Откат транзакции теста здесь не годится: проверки блокировок и конкурентного
 * резервирования требуют настоящих коммитов.
 *
 * <p>TRUNCATE не вызывает строковые триггеры, поэтому запрет на изменение журнала движений
 * очистке не мешает. {@code RESTART IDENTITY} сбрасывает только последовательности столбцов,
 * а нумерация приходов и волн ведётся отдельными, поэтому они перезапускаются явно.
 */
class TruncateTablesExtension implements BeforeEachCallback {

    private static final String TABLES = """
            select string_agg(format('%I.%I', schemaname, tablename), ', ')
            from pg_tables
            where schemaname = 'public' and tablename <> 'flyway_schema_history'
            """;

    private static final String SEQUENCES = """
            select format('alter sequence %I.%I restart', schemaname, sequencename)
            from pg_sequences
            where schemaname = 'public'
            """;

    @Override
    public void beforeEach(ExtensionContext context) {
        JdbcTemplate jdbc = SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
        String tables = jdbc.queryForObject(TABLES, String.class);
        if (tables != null) {
            jdbc.execute("truncate " + tables + " restart identity cascade");
        }
        List<String> restarts = jdbc.queryForList(SEQUENCES, String.class);
        restarts.forEach(jdbc::execute);
    }
}
