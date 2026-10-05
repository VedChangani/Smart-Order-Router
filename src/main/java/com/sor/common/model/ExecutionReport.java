package com.sor.common.model;

import java.util.List;

/**
 * Returned by the Smart Order Router to a Trading Client describing how an
 * order was (or was not) executed, including the per-exchange breakdown.
 */
public class ExecutionReport {

    private String orderId;
    private String clientId;
    private String symbol;
    private OrderSide side;
    private int requestedQuantity;
    private int executedQuantity;
    private OrderStatus status;
    private List<Fill> fills;
    private double averagePrice;
    private double totalValue;
    private String message;

    public ExecutionReport() {
        // Required by Jackson for deserialization.
    }

    public ExecutionReport(String orderId, String clientId, String symbol, OrderSide side,
                            int requestedQuantity, int executedQuantity, OrderStatus status,
                            List<Fill> fills, double averagePrice, double totalValue, String message) {
        this.orderId = orderId;
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.requestedQuantity = requestedQuantity;
        this.executedQuantity = executedQuantity;
        this.status = status;
        this.fills = fills;
        this.averagePrice = averagePrice;
        this.totalValue = totalValue;
        this.message = message;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(int requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public int getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(int executedQuantity) {
        this.executedQuantity = executedQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<Fill> getFills() {
        return fills;
    }

    public void setFills(List<Fill> fills) {
        this.fills = fills;
    }

    public double getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(double averagePrice) {
        this.averagePrice = averagePrice;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(double totalValue) {
        this.totalValue = totalValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ExecutionReport{orderId='" + orderId + "', symbol='" + symbol + "', side=" + side
                + ", status=" + status + ", executed=" + executedQuantity + "/" + requestedQuantity + '}';
    }
}
