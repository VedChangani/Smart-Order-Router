package com.sor.common.config;

import com.sor.common.protocol.ProtocolConstants;

import java.io.IOException;
import java.util.List;

/**
 * All configuration the Smart Order Router needs to start up: which port to
 * listen on for Trading Clients, which exchanges to query, and the timeouts
 * to apply when talking to those exchanges.
 */
public class RouterConfig {

    private final int port;
    private final List<ExchangeEndpoint> exchanges;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int queryTimeoutMs;

    public RouterConfig(int port, List<ExchangeEndpoint> exchanges, int connectTimeoutMs,
                         int readTimeoutMs, int queryTimeoutMs) {
        this.port = port;
        this.exchanges = exchanges;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.queryTimeoutMs = queryTimeoutMs;
    }

    public static RouterConfig load(String source) throws IOException {
        AppConfig config = new AppConfig(source);
        int port = config.getInt("router.port", ProtocolConstants.DEFAULT_ROUTER_PORT);
        List<ExchangeEndpoint> exchanges = ExchangeEndpoint.parseList(config.getRequiredString("router.exchanges"));
        int connectTimeoutMs = config.getInt("router.connectTimeoutMs", ProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS);
        int readTimeoutMs = config.getInt("router.readTimeoutMs", ProtocolConstants.DEFAULT_READ_TIMEOUT_MS);
        int queryTimeoutMs = config.getInt("router.queryTimeoutMs", ProtocolConstants.DEFAULT_QUERY_TIMEOUT_MS);
        return new RouterConfig(port, exchanges, connectTimeoutMs, readTimeoutMs, queryTimeoutMs);
    }

    public int getPort() {
        return port;
    }

    public List<ExchangeEndpoint> getExchanges() {
        return exchanges;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getQueryTimeoutMs() {
        return queryTimeoutMs;
    }
}
