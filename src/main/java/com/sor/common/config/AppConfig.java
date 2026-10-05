package com.sor.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Thin wrapper around java.util.Properties that loads configuration either
 * from a filesystem path (if it exists) or, failing that, from a classpath
 * resource of the same name. This lets the same code work whether the
 * project is run from an IDE, from `mvn exec:java`, or from the packaged jar.
 */
public class AppConfig {

    private static final Logger LOGGER = Logger.getLogger(AppConfig.class.getName());

    private final Properties properties = new Properties();

    public AppConfig(String source) throws IOException {
        Path path = Paths.get(source);
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            }
            LOGGER.info(() -> "Loaded configuration from file: " + source);
            return;
        }

        String resourceName = source.endsWith(".properties") ? source : source + ".properties";
        try (InputStream in = AppConfig.class.getResourceAsStream("/" + resourceName)) {
            if (in == null) {
                throw new IOException("Configuration source not found on disk or classpath: " + source);
            }
            properties.load(in);
        }
        LOGGER.info(() -> "Loaded configuration from classpath resource: " + resourceName);
    }

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public String getRequiredString(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration key: " + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }
}
