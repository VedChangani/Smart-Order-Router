package com.sor.common.model;

/**
 * Sent by the Smart Order Router to an Exchange Server to actually execute
 * (fill) a quantity at that exchange, as decided by the router's order
 * allocation plan.
 */
public class ExecuteRequest {

    private String orderId;
    private String symbol;
    private OrderSide side;
    private int quantity;
    private double expectedPrice;

    public ExecuteRequest() {
        // Required by Jackson for deserialization.
    }

    public ExecuteRequest(String orderId, String symbol, OrderSide side, int quantity, double expectedPrice) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.expectedPrice = expectedPrice;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getExpectedPrice() {
        return expectedPrice;
    }

    public void setExpectedPrice(double expectedPrice) {
        this.expectedPrice = expectedPrice;
    }

    @Override
    public String toString() {
        return "ExecuteRequest{orderId='" + orderId + "', symbol='" + symbol + "', side=" + side
                + ", quantity=" + quantity + '}';
    }
}
