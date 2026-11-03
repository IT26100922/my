package com.moviebooking.config.diagnostics;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import java.net.ConnectException;

/**
 * Turns "the database server is not running" into a short, readable message instead of two hundred lines
 * of stack trace.
 *
 * <p>When Spring Boot cannot open a JDBC connection it fails while building the JPA EntityManagerFactory,
 * and the sentence that matters ("Connection refused") is buried at the bottom of the stack. This analyzer
 * is listed in {@code META-INF/spring.factories}, so Spring Boot calls it when startup fails and prints a
 * description plus the two or three things that actually fix it.</p>
 *
 * <p>It reads the same DB_HOST / DB_PORT / DB_USER variables the application itself reads, so the message
 * shows the settings that were really used.</p>
 *
 * <p>සිංහල: දත්ත සමුදාය (MySQL) ක්‍රියාත්මක නොවන විට පෙන්වන දිගු දෝෂ ලැයිස්තුව වෙනුවට, තත්ත්වය සහ කළ යුතු දේ
 * කෙටියෙන් පැහැදිලි කරන පණිවිඩයක් මෙයින් මුද්‍රණය කෙරේ.</p>
 */
public class DatabaseUnreachableFailureAnalyzer extends AbstractFailureAnalyzer<ConnectException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ConnectException cause) {
        String host = property("DB_HOST", "localhost");
        String port = property("DB_PORT", "3306");
        String user = property("DB_USER", "root");

        String description = """
                CineMax stopped because it cannot reach the MySQL server.
                  Nothing is listening at %s:%s, so the connection was refused.
                  Settings used: DB_HOST=%s  DB_PORT=%s  DB_USER=%s  (password from DB_PASS, default 0000)
                The application is fine - the database is simply not running yet."""
                .formatted(host, port, host, port, user);

        String action = """
                1. Start MySQL, then run the project again.
                     Windows:  net start MySQL80        (service names: MySQL80, MySQL84, MySQL57 ...;
                                                        Win+R -> services.msc lists the real name)
                     Linux:    sudo service mysql start
                     macOS:    brew services start mysql
                2. Check the port is 3306 and that something answers on it:
                     Windows:  netstat -ano | findstr :3306
                     Linux/macOS:  sudo lsof -i :3306
                3. Different port, host or password? Pass it to the run configuration
                   (IntelliJ: Run -> Edit Configurations -> Environment variables):
                     DB_PORT=3307   DB_USER=root   DB_PASS=yourpassword
                4. No MySQL installed? Install MySQL 8
                   (https://dev.mysql.com/downloads/installer/), or run everything in Docker:
                     docker compose up --build
                Then open http://localhost:8080 and log in as admin@cinema.lk / admin123.""";

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
