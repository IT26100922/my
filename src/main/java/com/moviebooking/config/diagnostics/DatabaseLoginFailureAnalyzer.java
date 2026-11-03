package com.moviebooking.config.diagnostics;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.sql.SQLException;

/**
 * The other half of the database problems: the server is running, but it said no.
 *
 * <p>Almost always one of two things - the root password is not the {@code 0000} this project ships with,
 * or the database user was never created. Both are one line to fix, and this analyzer says which.</p>
 *
 * <p>It is registered for {@link SQLException} because that is what the MySQL driver actually throws for
 * "Access denied" (SQLState 28000), and it returns {@code null} for every other SQL error so Spring Boot's
 * usual handling is untouched.</p>
 *
 * <p>සිංහල: MySQL ක්‍රියාත්මක වුවත් මුරපදය හෝ පරිශීලක නාමය වැරදි විට, එය නිවැරදි කරන ආකාරය කෙටියෙන් පෙන්වයි.</p>
 */
public class DatabaseLoginFailureAnalyzer extends AbstractFailureAnalyzer<SQLException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SQLException cause) {
        boolean refused = cause.getMessage() != null && cause.getMessage().toLowerCase().contains("access denied");
        boolean authState = cause.getSQLState() != null && cause.getSQLState().startsWith("28");
        if (!refused && !authState) {
            return null;   // not our business: let Spring Boot report it
        }
        String user = property("DB_USER", "root");
        String description = "MySQL is running, but it refused the login for user '%s'.\n".formatted(user)
                + "  The project ships with root / 0000. If your MySQL root password is something else,\n"
                + "  the application has to be told.";
        String action = """
                1. Tell the application the real password (IntelliJ: Run -> Edit Configurations ->
                   Environment variables), and add DB_USER too if you log in as another user:
                     DB_USER=root   DB_PASS=yourpassword
                2. Or change the default once in src/main/resources/application.properties:
                     spring.datasource.password=${DB_PASS:0000}
                3. Forgot the root password? Either reset it with the MySQL installer, or create a
                   dedicated user and run with it:
                     CREATE USER 'cinemax'@'localhost' IDENTIFIED BY 'Cinemax#2026';
                     GRANT ALL PRIVILEGES ON movie_booking.* TO 'cinemax'@'localhost';
                     FLUSH PRIVILEGES;
                   then: DB_USER=cinemax  DB_PASS=Cinemax#2026
                Then start the application again.""";
        return new FailureAnalysis(description, action, cause);
    }

    /** Reads an environment variable, falling back to a system property, then to the given default. */
    private static String property(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
