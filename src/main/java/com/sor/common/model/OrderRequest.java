package com.sor.common.model;

/**
 * Sent by a Trading Client to the Smart Order Router to request execution
 * of an order.
 */
public class OrderRequest {

    private String clientId;
    private String symbol;
    private OrderSide side;
    private int quantity;

    public OrderRequest() {
        // Required by Jackson for deserialization.
    }

    public OrderRequest(String clientId, String symbol, OrderSide side, int quantity) {
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "OrderRequest{clientId='" + clientId + "', symbol='" + symbol + "', side=" + side
                + ", quantity=" + quantity + '}';
    }
}
