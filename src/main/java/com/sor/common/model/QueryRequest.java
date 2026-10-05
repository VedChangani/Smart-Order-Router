package com.sor.common.model;

/**
 * Sent by the Smart Order Router to an Exchange Server to ask what price and
 * quantity are available for a given symbol and side.
 */
public class QueryRequest {

    private String symbol;
    private OrderSide side;
    private int quantity;

    public QueryRequest() {
        // Required by Jackson for deserialization.
    }

    public QueryRequest(String symbol, OrderSide side, int quantity) {
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
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

    @Override
    public String toString() {
        return "QueryRequest{symbol='" + symbol + "', side=" + side + ", quantity=" + quantity + '}';
    }
}
