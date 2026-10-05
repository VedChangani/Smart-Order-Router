package com.sor.exchange;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of every symbol an exchange trades, keyed by symbol.
 */
public class Inventory {

    private final Map<String, InventoryItem> items = new ConcurrentHashMap<>();

    public void addItem(InventoryItem item) {
        items.put(item.getSymbol().toUpperCase(), item);
    }

    public Optional<InventoryItem> find(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(symbol.toUpperCase()));
    }

    public Collection<InventoryItem> all() {
        return items.values();
    }
}
