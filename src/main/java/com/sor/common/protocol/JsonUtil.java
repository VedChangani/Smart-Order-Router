package com.sor.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides a single, shared, thread-safe Jackson ObjectMapper for the whole
 * application. ObjectMapper instances are expensive to create and are safe
 * to reuse concurrently once configured, so we build it once here.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    private JsonUtil() {
        // Utility class; no instances.
    }
}
