package com.sor.router;

import com.sor.common.model.OrderSide;
import com.sor.common.model.QueryResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implements Smart Order Routing's core decision: given quotes gathered from
 * every exchange, allocate the requested quantity across exchanges starting
 * with the best price, filling as much as each exchange can offer before
 * moving to the next, until the order is fully filled or liquidity runs out
 * (Stage 7: order splitting).
 *
 * "Best price" means the lowest price for a BUY (cheapest to buy from) and
 * the highest price for a SELL (best price to sell into).
 */
public final class OrderAllocator {

    public static List<Allocation> allocate(List<QueryResponse> quotes, OrderSide side, int requestedQuantity) {
        List<QueryResponse> viable = new ArrayList<>();
        for (QueryResponse quote : quotes) {
            if (quote.isSuccess() && quote.getAvailableQuantity() > 0) {
                viable.add(quote);
            }
        }

        Comparator<QueryResponse> byBestPrice = side == OrderSide.BUY
                ? Comparator.comparingDouble(QueryResponse::getPrice)
                : Comparator.comparingDouble(QueryResponse::getPrice).reversed();
        viable.sort(byBestPrice);

        List<Allocation> allocations = new ArrayList<>();
        int remaining = requestedQuantity;
        for (QueryResponse quote : viable) {
            if (remaining <= 0) {
                break;
            }
            int quantity = Math.min(remaining, quote.getAvailableQuantity());
            if (quantity <= 0) {
                continue;
            }
            allocations.add(new Allocation(quote.getExchangeName(), quantity, quote.getPrice()));
            remaining -= quantity;
        }
        return allocations;
    }

    private OrderAllocator() {
        // Utility class; no instances.
    }
}
