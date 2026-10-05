package com.sor.client;

import com.sor.common.model.ExecutionReport;
import com.sor.common.model.OrderRequest;
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
import java.nio.charset.StandardCharsets;

/**
 * A single persistent TCP connection from a Trading Client to the Smart
 * Order Router. Kept open for the whole interactive session so several
 * orders can be sent without reconnecting each time.
 */
public class RouterConnector implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public RouterConnector(String host, int port, int connectTimeoutMs) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public ExecutionReport sendOrder(OrderRequest request) throws IOException {
        MessageParser.writeMessage(writer, MessageParser.buildMessage(MessageType.ORDER_REQUEST, request));
        ProtocolMessage response = MessageParser.readMessage(reader);
        if (response == null) {
            throw new IOException("Router closed the connection without responding");
        }
        if (response.getType() == MessageType.ERROR) {
            String reason = MessageParser.extractPayload(response, String.class);
            throw new IOException("Router rejected the order: " + reason);
        }
        return MessageParser.extractPayload(response, ExecutionReport.class);
    }

    @Override
    public void close() throws IOException {
        reader.close();
        writer.close();
        socket.close();
    }
}
