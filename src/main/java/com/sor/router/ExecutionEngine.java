package com.sor.router;

import com.sor.common.config.ExchangeEndpoint;
import com.sor.common.config.RouterConfig;
import com.sor.common.model.ExecuteRequest;
import com.sor.common.model.ExecuteResponse;
import com.sor.common.model.ExecutionReport;
import com.sor.common.model.Fill;
import com.sor.common.model.OrderRequest;
import com.sor.common.model.OrderStatus;
import com.sor.common.model.QueryResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The heart of the Smart Order Router: given an OrderRequest from a client,
 * this class queries all exchanges in parallel, builds an allocation plan
 * across the best-priced exchanges (splitting the order if one exchange
 * cannot fill it alone), sends out the actual EXECUTE_REQUESTs, and reduces
 * the results into a single ExecutionReport for the client. Also implements
 * Stage 8's graceful degradation: unreachable exchanges are skipped rather
 * than failing the whole order, and a shortfall in total liquidity results
 * in a PARTIALLY_FILLED report instead of an exception.
 */
public class ExecutionEngine {

    private static final Logger LOGGER = Logger.getLogger(ExecutionEngine.class.getName());
    private static final AtomicLong ORDER_ID_SEQUENCE = new AtomicLong(1000);

    private final ExchangeQueryService queryService;
    private final Map<String, ExchangeConnector> connectorsByName;
    private final ExecutorService executionExecutor;

    public ExecutionEngine(RouterConfig config) {
        this.queryService = new ExchangeQueryService(config.getExchanges(), config.getConnectTimeoutMs(),
                config.getReadTimeoutMs(), config.getQueryTimeoutMs());
        this.connectorsByName = new HashMap<>();
        for (ExchangeEndpoint endpoint : config.getExchanges()) {
            connectorsByName.put(endpoint.getName(),
                    new ExchangeConnector(endpoint, config.getConnectTimeoutMs(), config.getReadTimeoutMs()));
        }
        this.executionExecutor = Executors.newFixedThreadPool(Math.max(connectorsByName.size(), 1));
    }

    public ExecutionReport process(OrderRequest orderRequest) {
        String orderId = "ORD-" + ORDER_ID_SEQUENCE.getAndIncrement();
        LOGGER.info(() -> "Processing " + orderId + ": " + orderRequest);

        List<QueryResponse> quotes = queryService.queryAll(orderRequest.getSymbol(), orderRequest.getSide(),
                orderRequest.getQuantity());

        boolean anyExchangeReachable = quotes.stream().anyMatch(QueryResponse::isSuccess);
        if (!anyExchangeReachable) {
            return rejected(orderId, orderRequest,
                    "No exchanges were reachable or had liquidity for " + orderRequest.getSymbol());
        }

        List<Allocation> plan = OrderAllocator.allocate(quotes, orderRequest.getSide(), orderRequest.getQuantity());
        if (plan.isEmpty()) {
            return rejected(orderId, orderRequest,
                    "No liquidity available for " + orderRequest.getSymbol() + " on any reachable exchange");
        }

        // Submit every allocation's execution request up front so they run
        // concurrently against their respective exchanges, instead of
        // waiting for exchange A to finish before starting exchange B.
        List<Allocation> submittedAllocations = new ArrayList<>();
        List<Future<ExecuteResponse>> executionFutures = new ArrayList<>();
        for (Allocation allocation : plan) {
            ExchangeConnector connector = connectorsByName.get(allocation.getExchangeName());
            if (connector == null) {
                LOGGER.warning(() -> "No connector configured for " + allocation.getExchangeName() + ", skipping");
                continue;
            }

            ExecuteRequest executeRequest = new ExecuteRequest(orderId, orderRequest.getSymbol(),
                    orderRequest.getSide(), allocation.getQuantity(), allocation.getPrice());
            Callable<ExecuteResponse> task = () -> connector.execute(executeRequest);
            submittedAllocations.add(allocation);
            executionFutures.add(executionExecutor.submit(task));
        }

        List<Fill> fills = new ArrayList<>();
        int totalExecuted = 0;
        double totalValue = 0.0;

        // Collect results in the same order allocations were submitted, so
        // aggregation (fills list, totals) stays deterministic even though
        // the underlying exchange calls completed concurrently and possibly
        // out of order.
        for (int i = 0; i < executionFutures.size(); i++) {
            Allocation allocation = submittedAllocations.get(i);
            ExecuteResponse response;
            try {
                response = executionFutures.get(i).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning(() -> "Interrupted while waiting for execution at " + allocation.getExchangeName()
                        + " for " + orderId);
                continue;
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "Unexpected error executing at " + allocation.getExchangeName()
                        + " for " + orderId, e);
                continue;
            }

            if (response.isSuccess() && response.getExecutedQuantity() > 0) {
                fills.add(new Fill(response.getExchangeName(), response.getExecutedQuantity(), response.getExecutedPrice()));
                totalExecuted += response.getExecutedQuantity();
                totalValue += response.getExecutedQuantity() * response.getExecutedPrice();
            } else {
                LOGGER.warning(() -> "Execution failed at " + allocation.getExchangeName() + " for " + orderId
                        + ": " + response.getMessage());
            }
        }

        OrderStatus status;
        if (totalExecuted == 0) {
            status = OrderStatus.REJECTED;
        } else if (totalExecuted < orderRequest.getQuantity()) {
            status = OrderStatus.PARTIALLY_FILLED;
        } else {
            status = OrderStatus.FILLED;
        }

        double averagePrice = totalExecuted > 0 ? totalValue / totalExecuted : 0.0;
        String message = buildMessage(status, totalExecuted, orderRequest.getQuantity(), fills.size());

        ExecutionReport report = new ExecutionReport(orderId, orderRequest.getClientId(), orderRequest.getSymbol(),
                orderRequest.getSide(), orderRequest.getQuantity(), totalExecuted, status, fills, averagePrice,
                totalValue, message);

        int finalExecuted = totalExecuted;
        LOGGER.info(() -> "Completed " + orderId + ": " + status + " (" + finalExecuted + "/"
                + orderRequest.getQuantity() + ")");
        return report;
    }

    private String buildMessage(OrderStatus status, int executed, int requested, int exchangeCount) {
        return switch (status) {
            case FILLED -> "Order fully executed across " + exchangeCount + " exchange(s)";
            case PARTIALLY_FILLED -> "Order partially executed: " + executed + "/" + requested
                    + " shares filled across " + exchangeCount + " exchange(s)";
            case REJECTED -> "Order could not be executed";
        };
    }

    private ExecutionReport rejected(String orderId, OrderRequest request, String reason) {
        LOGGER.warning(() -> orderId + " rejected: " + reason);
        return new ExecutionReport(orderId, request.getClientId(), request.getSymbol(), request.getSide(),
                request.getQuantity(), 0, OrderStatus.REJECTED, new ArrayList<>(), 0.0, 0.0, reason);
    }

    public void shutdown() {
        queryService.shutdown();
        executionExecutor.shutdown();
    }
}
