package com.sor.common.protocol;

/**
 * Identifies the payload type carried inside a ProtocolMessage envelope so
 * the receiver knows which class to deserialize the JSON payload into.
 */
public enum MessageType {
    ORDER_REQUEST,
    EXECUTION_REPORT,
    QUERY_REQUEST,
    QUERY_RESPONSE,
    EXECUTE_REQUEST,
    EXECUTE_RESPONSE,
    ERROR
}
