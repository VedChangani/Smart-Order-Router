package com.sor.exchange;

import com.sor.common.model.ExecuteRequest;
import com.sor.common.model.ExecuteResponse;
import com.sor.common.model.QueryRequest;
import com.sor.common.model.QueryResponse;
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
 * Handles a single TCP connection from the Smart Order Router (or any other
 * client speaking the protocol): reads ProtocolMessages in a loop, dispatches
 * QUERY_REQUEST / EXECUTE_REQUEST to the ExchangeService, and writes back the
 * corresponding response. Runs on its own thread from the exchange's
 * ExecutorService, so exchanges can serve many router connections at once.
 */
public class ExchangeClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ExchangeClientHandler.class.getName());

    private final Socket socket;
    private final ExchangeService service;

    public ExchangeClientHandler(Socket socket, ExchangeService service) {
        this.socket = socket;
        this.service = service;
    }

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress().toString();
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
            LOGGER.fine(() -> "Connection closed: " + remote);
        }
    }

    private void handleMessage(ProtocolMessage message, BufferedWriter writer) throws IOException {
        try {
            switch (message.getType()) {
                case QUERY_REQUEST -> handleQuery(message, writer);
                case EXECUTE_REQUEST -> handleExecute(message, writer);
                default -> writeError(writer, "Exchange does not accept messages of type " + message.getType());
            }
        } catch (ValidationException e) {
            writeError(writer, e.getMessage());
        }
    }

    private void handleQuery(ProtocolMessage message, BufferedWriter writer) throws IOException, ValidationException {
        QueryRequest request = MessageParser.extractPayload(message, QueryRequest.class);
        MessageValidator.validateQueryRequest(request);
        QueryResponse response = service.handleQuery(request);
        MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.QUERY_RESPONSE, response));
    }

    private void handleExecute(ProtocolMessage message, BufferedWriter writer) throws IOException, ValidationException {
        ExecuteRequest request = MessageParser.extractPayload(message, ExecuteRequest.class);
        MessageValidator.validateExecuteRequest(request);
        ExecuteResponse response = service.handleExecute(request);
        MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.EXECUTE_RESPONSE, response));
    }

    private void writeError(BufferedWriter writer, String reason) throws IOException {
        MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.ERROR, reason));
    }
}
