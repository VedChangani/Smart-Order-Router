package com.sor.common.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Network location and logical name of one Exchange Server, as known by the
 * Smart Order Router.
 */
public class ExchangeEndpoint {

    private final String name;
    private final String host;
    private final int port;

    public ExchangeEndpoint(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Parses a comma-separated list of "NAME:HOST:PORT" tokens, e.g.
     * "NYSE:localhost:7001,NASDAQ:localhost:7002".
     */
    public static List<ExchangeEndpoint> parseList(String raw) {
        List<ExchangeEndpoint> endpoints = new ArrayList<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid exchange endpoint definition: '" + trimmed
                        + "' (expected NAME:HOST:PORT)");
            }
            endpoints.add(new ExchangeEndpoint(parts[0], parts[1], Integer.parseInt(parts[2])));
        }
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("No exchange endpoints were configured");
        }
        return endpoints;
    }

    @Override
    public String toString() {
        return name + "(" + host + ":" + port + ")";
    }
}
