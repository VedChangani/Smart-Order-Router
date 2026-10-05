package com.sor.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * Loads java.util.logging configuration from the bundled logging.properties
 * classpath resource. Each main() entry point calls init() once at startup.
 * If the resource cannot be found, the JVM's default logging configuration
 * is left in place instead of failing startup.
 */
public final class LoggingConfig {

    public static void init() {
        try (InputStream in = LoggingConfig.class.getResourceAsStream("/logging.properties")) {
            if (in != null) {
                LogManager.getLogManager().readConfiguration(in);
            }
        } catch (IOException e) {
            System.err.println("Could not load logging configuration, using defaults: " + e.getMessage());
        }
    }

    private LoggingConfig() {
        // Utility class; no instances.
    }
}
