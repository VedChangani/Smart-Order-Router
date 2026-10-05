package com.sor.common.model;

/**
 * A single execution slice: the quantity filled at a specific exchange and
 * the price it was filled at. An ExecutionReport contains one Fill per
 * exchange that participated in satisfying the order.
 */
public class Fill {

    private String exchangeName;
    private int quantity;
    private double price;

    public Fill() {
        // Required by Jackson for deserialization.
    }

    public Fill(String exchangeName, int quantity, double price) {
        this.exchangeName = exchangeName;
        this.quantity = quantity;
        this.price = price;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Fill{exchange='" + exchangeName + "', quantity=" + quantity + ", price=" + price + '}';
    }
}
