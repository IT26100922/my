package com.moviebooking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Makes sure a brand-new database is never accountless.
 *
 * <p>The application creates its own tables ({@code ddl-auto=update}), but the three MySQL <em>import</em>
 * tables (import_movies, import_users, import_showtimes) are normally filled by
 * {@code database/cinemax_mysql_setup.sql}. Start the application without running that script and the
 * tables exist and are empty - nothing is imported, there is not one account, and every login answers
 * "Invalid email or password". That looks like a broken login and is really an empty database.</p>
 *
 * <p>This seeder closes the gap: when the import tables are empty it runs the built-in copy of the sample
 * rows ({@code classpath:seed/sample-import-tables.sql}) and the normal import path does the rest. On a
 * database that already has data it does nothing at all.</p>
 *
 * <p>සිංහල: නව දත්ත සමුදායක් සෑදූ විට ආයාත වගු හිස් නම්, ආදර්ශ දත්ත ස්වයංක්‍රීයව ඇතුළත් කරයි. එමනිසා
 * පද්ධතියේ ගිණුම් සැමවිටම තිබේ. දැනටමත් දත්ත ඇති විට කිසිවක් වෙනස් නොකරයි.</p>
 */
@Component
public class SampleImportSeeder {

    private static final Logger log = LoggerFactory.getLogger(SampleImportSeeder.class);
    private static final String SCRIPT = "seed/sample-import-tables.sql";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public SampleImportSeeder(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    /** True when none of the three import tables has a row. */
    public boolean stagingIsEmpty() {
        return count("import_users") + count("import_movies") + count("import_showtimes") == 0;
    }

    /**
     * Loads the sample rows into the import tables when they are empty.
     *
     * @return how many account rows are waiting in import_users afterwards
     */
    public long seedIfEmpty() {
        if (!stagingIsEmpty()) {
            return count("import_users");
        }
        log.info("The MySQL import tables are empty, so the built-in sample rows are loaded ({}). "
                + "This is the same data SECTION 4 of database/cinemax_mysql_setup.sql writes.", SCRIPT);
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCRIPT));
                connection.commit();
            } catch (RuntimeException e) {
                connection.rollback();
                log.error("The built-in sample rows could not be loaded ({}). The application still starts: "
                        + "run database/cinemax_mysql_setup.sql, then use Admin -> Files & Reports -> Import.",
                        e.getMessage(), e);
                return 0;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            log.error("Could not open a connection to load the sample rows: {}", e.getMessage(), e);
            return 0;
        }
        log.info("Built-in sample rows loaded: {} accounts, {} films, {} showtimes "
                        + "(logins: admin@cinema.lk/admin123, staff@cinema.lk/staff123, "
                        + "door@cinema.lk/door1234, customer@cinema.lk/customer123)",
                count("import_users"), count("import_movies"), count("import_showtimes"));
        return count("import_users");
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }
}
