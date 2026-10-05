package com.sor.common.protocol;

/**
 * Wire-level envelope for every message exchanged over a socket in this
 * system. The payload is carried as an already-serialized JSON string so
 * that a single generic envelope type can transport any DTO without Java
 * generics-over-the-wire complications; the receiver looks at "type" to
 * decide which concrete class to parse "payload" into.
 */
public class ProtocolMessage {

    private MessageType type;
    private String payload;

    public ProtocolMessage() {
        // Required by Jackson for deserialization.
    }

    public ProtocolMessage(MessageType type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "ProtocolMessage{type=" + type + ", payload=" + payload + '}';
    }
}
