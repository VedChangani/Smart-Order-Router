package com.sor.common.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Shared protocol-level and networking constants used across the exchange,
 * router and client modules.
 */
public final class ProtocolConstants {

    public static final Charset WIRE_CHARSET = StandardCharsets.UTF_8;

    public static final int DEFAULT_ROUTER_PORT = 6000;

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 2000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 3000;
    public static final int DEFAULT_QUERY_TIMEOUT_MS = 3000;

    private ProtocolConstants() {
        // Utility class; no instances.
    }
}
