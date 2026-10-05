package com.sor.router;

import com.sor.common.config.ExchangeEndpoint;
import com.sor.common.model.ExecuteRequest;
import com.sor.common.model.ExecuteResponse;
import com.sor.common.model.QueryRequest;
import com.sor.common.model.QueryResponse;
import com.sor.common.protocol.MessageParser;
import com.sor.common.protocol.MessageType;
import com.sor.common.protocol.ProtocolMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The router acting as a TCP *client* to a single exchange: opens a fresh
 * socket per call (simple, stateless, and safe to invoke concurrently from
 * multiple threads for different exchanges), sends one request, reads one
 * response, and closes. Connect and read timeouts are enforced so a slow or
 * dead exchange cannot block the router indefinitely (Stage 8: failure
 * handling).
 */
public class ExchangeConnector {

    private static final Logger LOGGER = Logger.getLogger(ExchangeConnector.class.getName());

    private final ExchangeEndpoint endpoint;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private volatile Socket activeSocket;

    public ExchangeConnector(ExchangeEndpoint endpoint, int connectTimeoutMs, int readTimeoutMs) {
        this.endpoint = endpoint;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getExchangeName() {
        return endpoint.getName();
    }

    /**
     * Forcibly closes the socket currently in use by a query() call on this
     * connector, if any. Thread interruption alone does not reliably unblock
     * a thread stuck in a blocking Socket read, but closing the socket does
     * (it causes the pending read to fail immediately). Used by
     * ExchangeQueryService when the global query deadline expires.
     */
    public void cancelPendingQuery() {
        Socket socket = activeSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort: the goal is only to unblock the pending read.
            }
        }
    }

    public QueryResponse query(QueryRequest request) {
        try (Socket socket = openSocket()) {
            activeSocket = socket;
            try (BufferedReader reader = reader(socket); BufferedWriter writer = writer(socket)) {
                MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.QUERY_REQUEST, request));
                ProtocolMessage response = MessageParser.readMessage(reader);
                if (response == null) {
                    return failedQuery(request, endpoint.getName() + " closed the connection without responding");
                }
                if (response.getType() == MessageType.ERROR) {
                    String reason = MessageParser.extractPayload(response, String.class);
                    return failedQuery(request, endpoint.getName() + " rejected the query: " + reason);
                }
                return MessageParser.extractPayload(response, QueryResponse.class);
            }
        } catch (SocketTimeoutException e) {
            LOGGER.warning(() -> "Timeout querying " + endpoint.getName() + " for " + request.getSymbol());
            return failedQuery(request, "Timed out waiting for " + endpoint.getName());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, endpoint.getName() + " is unreachable for query", e);
            return failedQuery(request, endpoint.getName() + " is unavailable: " + e.getMessage());
        } finally {
            activeSocket = null;
        }
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        try (Socket socket = openSocket()) {
            try (BufferedReader reader = reader(socket); BufferedWriter writer = writer(socket)) {
                MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.EXECUTE_REQUEST, request));
                ProtocolMessage response = MessageParser.readMessage(reader);
                if (response == null) {
                    return failedExecute(request, endpoint.getName() + " closed the connection without responding");
                }
                if (response.getType() == MessageType.ERROR) {
                    String reason = MessageParser.extractPayload(response, String.class);
                    return failedExecute(request, endpoint.getName() + " rejected the execution: " + reason);
                }
                return MessageParser.extractPayload(response, ExecuteResponse.class);
            }
        } catch (SocketTimeoutException e) {
            LOGGER.warning(() -> "Timeout executing on " + endpoint.getName() + " for order " + request.getOrderId());
            return failedExecute(request, "Timed out waiting for " + endpoint.getName());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, endpoint.getName() + " is unreachable for execution", e);
            return failedExecute(request, endpoint.getName() + " is unavailable: " + e.getMessage());
        }
    }

    private Socket openSocket() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        return socket;
    }

    private BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private QueryResponse failedQuery(QueryRequest request, String message) {
        return new QueryResponse(endpoint.getName(), request.getSymbol(), request.getSide(), 0.0, 0, false, message);
    }

    private ExecuteResponse failedExecute(ExecuteRequest request, String message) {
        return new ExecuteResponse(endpoint.getName(), request.getSymbol(), request.getSide(), 0, 0.0, false, message);
    }
}
