package com.sor.router;

/**
 * One slice of an order allocation plan: fill this much quantity at this
 * exchange, at the price it quoted. Produced by OrderAllocator, consumed by
 * ExecutionEngine when it sends ExecuteRequests out to exchanges.
 */
public class Allocation {

    private final String exchangeName;
    private final int quantity;
    private final double price;

    public Allocation(String exchangeName, int quantity, double price) {
        this.exchangeName = exchangeName;
        this.quantity = quantity;
        this.price = price;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return "Allocation{exchange='" + exchangeName + "', quantity=" + quantity + ", price=" + price + '}';
    }
}
