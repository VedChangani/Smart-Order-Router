package com.sor.router;

import com.sor.common.model.ExecutionReport;
import com.sor.common.model.OrderRequest;
import com.sor.common.protocol.MessageParser;
import com.sor.common.protocol.MessageType;
import com.sor.common.protocol.MessageValidator;
import com.sor.common.protocol.ProtocolMessage;
import com.sor.common.protocol.ValidationException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles one Trading Client connection to the Smart Order Router. A client
 * may keep its connection open and send several orders in sequence (one
 * ORDER_REQUEST per line, one EXECUTION_REPORT back per line) until it
 * disconnects. Runs on its own thread from the router's ExecutorService so
 * the router can serve many clients concurrently.
 */
public class RouterClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(RouterClientHandler.class.getName());

    private final Socket socket;
    private final ExecutionEngine engine;

    public RouterClientHandler(Socket socket, ExecutionEngine engine) {
        this.socket = socket;
        this.engine = engine;
    }

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress().toString();
        LOGGER.info(() -> "Client connected: " + remote);
        try (Socket s = socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {

            ProtocolMessage message;
            while ((message = MessageParser.readMessage(reader)) != null) {
                handleMessage(message, writer);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Connection error with " + remote, e);
        } finally {
            LOGGER.info(() -> "Client disconnected: " + remote);
        }
    }

    private void handleMessage(ProtocolMessage message, BufferedWriter writer) throws IOException {
        if (message.getType() != MessageType.ORDER_REQUEST) {
            writeError(writer, "Router only accepts messages of type ORDER_REQUEST, got " + message.getType());
            return;
        }
        try {
            OrderRequest request = MessageParser.extractPayload(message, OrderRequest.class);
            MessageValidator.validateOrderRequest(request);
            ExecutionReport report = engine.process(request);
            MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.EXECUTION_REPORT, report));
        } catch (ValidationException e) {
            writeError(writer, e.getMessage());
        }
    }

    private void writeError(BufferedWriter writer, String reason) throws IOException {
        MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.ERROR, reason));
    }
}
