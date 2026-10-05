package com.sor.common.model;

/**
 * Returned by an Exchange Server in response to an ExecuteRequest, stating
 * how much was actually filled (which may be less than requested) and at
 * what price.
 */
public class ExecuteResponse {

    private String exchangeName;
    private String symbol;
    private OrderSide side;
    private int executedQuantity;
    private double executedPrice;
    private boolean success;
    private String message;

    public ExecuteResponse() {
        // Required by Jackson for deserialization.
    }

    public ExecuteResponse(String exchangeName, String symbol, OrderSide side, int executedQuantity,
                            double executedPrice, boolean success, String message) {
        this.exchangeName = exchangeName;
        this.symbol = symbol;
        this.side = side;
        this.executedQuantity = executedQuantity;
        this.executedPrice = executedPrice;
        this.success = success;
        this.message = message;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
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

    public int getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(int executedQuantity) {
        this.executedQuantity = executedQuantity;
    }

    public double getExecutedPrice() {
        return executedPrice;
    }

    public void setExecutedPrice(double executedPrice) {
        this.executedPrice = executedPrice;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ExecuteResponse{exchange='" + exchangeName + "', symbol='" + symbol + "', side=" + side
                + ", executed=" + executedQuantity + ", price=" + executedPrice + ", success=" + success + '}';
    }
}
