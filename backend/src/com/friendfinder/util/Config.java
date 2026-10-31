package com.friendfinder.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** One local file, with environment variables taking precedence. */
public final class Config {
    private static final Properties VALUES = new Properties();
    static {
        Path path = Path.of("config.local.properties");
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) { VALUES.load(input); }
            catch (IOException e) { throw new IllegalStateException("Cannot read local configuration", e); }
        }
    }
    private Config() {}
    public static String get(String key, String fallback) {
        String environment = System.getenv("FF_" + key.toUpperCase().replace('.', '_'));
        return environment != null ? environment : VALUES.getProperty(key, fallback);
    }
}
