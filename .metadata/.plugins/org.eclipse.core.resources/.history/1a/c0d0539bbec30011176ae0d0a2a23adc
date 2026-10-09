package com.posapp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hibernate creates a CHECK constraint listing an enum's values when it first creates the table, and
 * {@code ddl-auto=update} never changes it. When an existing enum gains a value (the stock transfer status
 * gained IN_TRANSIT and RECEIVED), saving the new value fails on a database created before that. This drops the
 * stale check constraint on each such column at startup; the application validates the values itself. Safe to run
 * on every start (nothing to drop the second time), PostgreSQL only, and never blocks startup.
 */
@Component
@Order(0)
public class EnumConstraintMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnumConstraintMigration.class);

    /** {table, column} pairs whose enum has grown since the table may have been created. */
    private static final List<String[]> COLUMNS = java.util.Collections.singletonList(
            new String[]{"stock_transfer", "status"}
    );

    private final JdbcTemplate jdbc;

    public EnumConstraintMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String[] tc : COLUMNS) {
            try {
                List<String> names = jdbc.queryForList(
                        "select c.conname from pg_constraint c " +
                                "join pg_class t on t.oid = c.conrelid " +
                                "where t.relname = ? and c.contype = 'c' and pg_get_constraintdef(c.oid) ilike ?",
                        String.class, tc[0], "%" + tc[1] + "%");
                for (String name : names) {
                    jdbc.execute("alter table " + tc[0] + " drop constraint \"" + name.replace("\"", "") + "\"");
                    log.info("Dropped stale enum check constraint {} on {}.{}", name, tc[0], tc[1]);
                }
            } catch (Exception e) {
                log.debug("Enum constraint cleanup skipped for {}.{}: {}", tc[0], tc[1], e.getMessage());
            }
        }
    }
}
