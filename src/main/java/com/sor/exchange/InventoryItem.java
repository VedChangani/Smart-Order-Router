package com.sor.exchange;

import com.sor.common.model.OrderSide;

/**
 * A single symbol's holdings at one exchange. availableQuantity is how much
 * stock the exchange currently has on hand and can sell to a client (i.e.
 * fill a client BUY order). maxCapacity is the warehouse ceiling: the
 * exchange can only buy stock from a client (fill a client SELL order) up to
 * (maxCapacity - availableQuantity) before it runs out of room. This gives
 * every exchange a genuinely limited capacity on both sides of the market,
 * which is what makes order splitting across exchanges necessary.
 *
 * All mutating operations are synchronized on the instance so that concurrent
 * client handler threads executing trades against the same symbol never
 * race each other.
 */
public class InventoryItem {

    private final String symbol;
    private volatile double price;
    private int availableQuantity;
    private final int maxCapacity;

    public InventoryItem(String symbol, double price, int availableQuantity, int maxCapacity) {
        this.symbol = symbol.toUpperCase();
        this.price = price;
        this.availableQuantity = availableQuantity;
        this.maxCapacity = maxCapacity;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    /**
     * How much of this side (BUY or SELL, from the client's perspective) the
     * exchange could currently fill, without changing any state.
     */
    public synchronized int getAvailableForSide(OrderSide side) {
        if (side == OrderSide.BUY) {
            return availableQuantity;
        }
        return Math.max(maxCapacity - availableQuantity, 0);
    }

    /**
     * Fills as much as possible of a client BUY order (the exchange sells
     * from its own stock) and returns the quantity actually filled, which
     * may be less than requested or zero.
     */
    public synchronized int fillClientBuy(int requestedQuantity) {
        int filled = Math.min(requestedQuantity, availableQuantity);
        availableQuantity -= filled;
        return filled;
    }

    /**
     * Fills as much as possible of a client SELL order (the exchange buys
     * into its own stock) and returns the quantity actually filled, which
     * may be less than requested or zero if the exchange has no more room.
     */
    public synchronized int fillClientSell(int requestedQuantity) {
        int room = Math.max(maxCapacity - availableQuantity, 0);
        int filled = Math.min(requestedQuantity, room);
        availableQuantity += filled;
        return filled;
    }
}
