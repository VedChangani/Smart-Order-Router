package com.sor.exchange;

import com.sor.common.model.ExecuteRequest;
import com.sor.common.model.ExecuteResponse;
import com.sor.common.model.QueryRequest;
import com.sor.common.model.QueryResponse;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Business logic of one Exchange Server: answering price/quantity queries
 * and executing trades against its Inventory. This class has no knowledge
 * of sockets or the wire protocol; it is exercised by ExchangeClientHandler.
 */
public class ExchangeService {

    private static final Logger LOGGER = Logger.getLogger(ExchangeService.class.getName());

    private final String exchangeName;
    private final Inventory inventory;

    public ExchangeService(String exchangeName, Inventory inventory) {
        this.exchangeName = exchangeName;
        this.inventory = inventory;
    }

    public QueryResponse handleQuery(QueryRequest request) {
        Optional<InventoryItem> maybeItem = inventory.find(request.getSymbol());
        if (maybeItem.isEmpty()) {
            return new QueryResponse(exchangeName, request.getSymbol(), request.getSide(), 0.0, 0,
                    false, exchangeName + " does not trade symbol " + request.getSymbol());
        }

        InventoryItem item = maybeItem.get();
        int available = item.getAvailableForSide(request.getSide());
        if (available <= 0) {
            return new QueryResponse(exchangeName, request.getSymbol(), request.getSide(), item.getPrice(), 0,
                    false, "No liquidity currently available on " + exchangeName);
        }

        return new QueryResponse(exchangeName, request.getSymbol(), request.getSide(), item.getPrice(),
                available, true, "OK");
    }

    public ExecuteResponse handleExecute(ExecuteRequest request) {
        Optional<InventoryItem> maybeItem = inventory.find(request.getSymbol());
        if (maybeItem.isEmpty()) {
            return new ExecuteResponse(exchangeName, request.getSymbol(), request.getSide(), 0, 0.0,
                    false, exchangeName + " does not trade symbol " + request.getSymbol());
        }

        InventoryItem item = maybeItem.get();
        int filled = request.getSide() == com.sor.common.model.OrderSide.BUY
                ? item.fillClientBuy(request.getQuantity())
                : item.fillClientSell(request.getQuantity());

        if (filled <= 0) {
            return new ExecuteResponse(exchangeName, request.getSymbol(), request.getSide(), 0,
                    item.getPrice(), false, "No liquidity available to execute on " + exchangeName);
        }

        String message = filled < request.getQuantity()
                ? "Partial fill: " + filled + " of " + request.getQuantity() + " requested"
                : "Filled in full";
        LOGGER.info(() -> exchangeName + " executed order " + request.getOrderId() + ": "
                + request.getSide() + " " + filled + " " + request.getSymbol() + " @ " + item.getPrice());

        return new ExecuteResponse(exchangeName, request.getSymbol(), request.getSide(), filled,
                item.getPrice(), true, message);
    }
}
