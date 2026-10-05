package com.sor.common.model;

/**
 * Returned by an Exchange Server in response to a QueryRequest. Also reused
 * internally by the Router as its in-memory "quote" representation when
 * comparing exchanges.
 */
public class QueryResponse {

    private String exchangeName;
    private String symbol;
    private OrderSide side;
    private double price;
    private int availableQuantity;
    private boolean success;
    private String message;

    public QueryResponse() {
        // Required by Jackson for deserialization.
    }

    public QueryResponse(String exchangeName, String symbol, OrderSide side, double price,
                          int availableQuantity, boolean success, String message) {
        this.exchangeName = exchangeName;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.availableQuantity = availableQuantity;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
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
        return "QueryResponse{exchange='" + exchangeName + "', symbol='" + symbol + "', side=" + side
                + ", price=" + price + ", available=" + availableQuantity + ", success=" + success + '}';
    }
}
