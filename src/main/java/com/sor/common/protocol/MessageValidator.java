package com.sor.common.protocol;

import com.sor.common.model.ExecuteRequest;
import com.sor.common.model.OrderRequest;
import com.sor.common.model.QueryRequest;

/**
 * Structural validation for the three request types that flow into the
 * system. Keeping this separate from the DTOs and from the handlers keeps
 * validation rules in one auditable place.
 */
public final class MessageValidator {

    public static void validateOrderRequest(OrderRequest request) throws ValidationException {
        if (request == null) {
            throw new ValidationException("Order request must not be null");
        }
        if (isBlank(request.getClientId())) {
            throw new ValidationException("Order request must include a clientId");
        }
        if (isBlank(request.getSymbol())) {
            throw new ValidationException("Order request must include a symbol");
        }
        if (request.getSide() == null) {
            throw new ValidationException("Order request must include a side (BUY or SELL)");
        }
        if (request.getQuantity() <= 0) {
            throw new ValidationException("Order request quantity must be greater than zero");
        }
    }

    public static void validateQueryRequest(QueryRequest request) throws ValidationException {
        if (request == null) {
            throw new ValidationException("Query request must not be null");
        }
        if (isBlank(request.getSymbol())) {
            throw new ValidationException("Query request must include a symbol");
        }
        if (request.getSide() == null) {
            throw new ValidationException("Query request must include a side (BUY or SELL)");
        }
        if (request.getQuantity() <= 0) {
            throw new ValidationException("Query request quantity must be greater than zero");
        }
    }

    public static void validateExecuteRequest(ExecuteRequest request) throws ValidationException {
        if (request == null) {
            throw new ValidationException("Execute request must not be null");
        }
        if (isBlank(request.getOrderId())) {
            throw new ValidationException("Execute request must include an orderId");
        }
        if (isBlank(request.getSymbol())) {
            throw new ValidationException("Execute request must include a symbol");
        }
        if (request.getSide() == null) {
            throw new ValidationException("Execute request must include a side (BUY or SELL)");
        }
        if (request.getQuantity() <= 0) {
            throw new ValidationException("Execute request quantity must be greater than zero");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private MessageValidator() {
        // Utility class; no instances.
    }
}
