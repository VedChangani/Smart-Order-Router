package com.sor.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * Builds and (de)serializes ProtocolMessage envelopes on top of a simple
 * newline-delimited JSON framing: exactly one JSON object per line. Jackson's
 * default (non pretty-printed) output never contains a raw newline, so a
 * single readLine()/writeLine() pair per message is sufficient framing for
 * this protocol.
 */
public final class MessageParser {

    public static ProtocolMessage buildMessage(MessageType type, Object payload) throws JsonProcessingException {
        String json = JsonUtil.getMapper().writeValueAsString(payload);
        return new ProtocolMessage(type, json);
    }

    public static <T> T extractPayload(ProtocolMessage message, Class<T> clazz) throws JsonProcessingException {
        return JsonUtil.getMapper().readValue(message.getPayload(), clazz);
    }

    public static void writeMessage(BufferedWriter writer, ProtocolMessage message) throws IOException {
        String json = JsonUtil.getMapper().writeValueAsString(message);
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    /**
     * Reads exactly one message from the stream, or returns null if the
     * stream has reached end-of-file (the peer closed the connection).
     */
    public static ProtocolMessage readMessage(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null || line.isBlank()) {
            return null;
        }
        return JsonUtil.getMapper().readValue(line, ProtocolMessage.class);
    }

    private MessageParser() {
        // Utility class; no instances.
    }
}
