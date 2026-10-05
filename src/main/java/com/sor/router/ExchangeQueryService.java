package com.sor.router;

import com.sor.common.config.ExchangeEndpoint;
import com.sor.common.model.OrderSide;
import com.sor.common.model.QueryRequest;
import com.sor.common.model.QueryResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Queries every configured exchange for a symbol/side/quantity in parallel
 * using an ExecutorService, instead of one-at-a-time, so the router's total
 * wait time is bounded by the slowest exchange rather than the sum of all
 * of them (Stage 6). Each exchange also has its own connect/read socket
 * timeouts (see ExchangeConnector) plus a router-level Future timeout here
 * as a second line of defense (Stage 8: failure handling).
 */
public class ExchangeQueryService {

    private static final Logger LOGGER = Logger.getLogger(ExchangeQueryService.class.getName());

    private final List<ExchangeConnector> connectors;
    private final ExecutorService executorService;
    private final int queryTimeoutMs;

    public ExchangeQueryService(List<ExchangeEndpoint> endpoints, int connectTimeoutMs, int readTimeoutMs,
                                 int queryTimeoutMs) {
        this.connectors = new ArrayList<>();
        for (ExchangeEndpoint endpoint : endpoints) {
            this.connectors.add(new ExchangeConnector(endpoint, connectTimeoutMs, readTimeoutMs));
        }
        this.executorService = Executors.newFixedThreadPool(Math.max(connectors.size(), 1));
        this.queryTimeoutMs = queryTimeoutMs;
    }

    /**
     * Fires a QUERY_REQUEST at every configured exchange concurrently and
     * collects all responses (successful or not) before returning. All
     * exchanges share a single global deadline (queryTimeoutMs from the
     * moment this method starts) instead of each being given its own fresh
     * timeout, so N slow/unresponsive exchanges cost at most ~queryTimeoutMs
     * in total rather than N * queryTimeoutMs. An unreachable or slow
     * exchange never prevents the others from being counted: it simply comes
     * back as a failed QueryResponse for that one exchange (graceful
     * degradation).
     */
    public List<QueryResponse> queryAll(String symbol, OrderSide side, int quantity) {
        QueryRequest request = new QueryRequest(symbol, side, quantity);

        CompletionService<QueryResponse> completionService = new ExecutorCompletionService<>(executorService);
        Map<Future<QueryResponse>, ExchangeConnector> pending = new HashMap<>();
        for (ExchangeConnector connector : connectors) {
            Callable<QueryResponse> task = () -> connector.query(request);
            pending.put(completionService.submit(task), connector);
        }

        Map<String, QueryResponse> resultsByExchange = new HashMap<>();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(queryTimeoutMs);

        while (!pending.isEmpty()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }
            Future<QueryResponse> completed;
            try {
                completed = completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (completed == null) {
                break; // global deadline reached, whatever hasn't finished is handled below
            }
            ExchangeConnector connector = pending.remove(completed);
            try {
                resultsByExchange.put(connector.getExchangeName(), completed.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                resultsByExchange.put(connector.getExchangeName(), new QueryResponse(connector.getExchangeName(),
                        symbol, side, 0.0, 0, false, "Interrupted while waiting for " + connector.getExchangeName()));
            } catch (CancellationException e) {
                resultsByExchange.put(connector.getExchangeName(), new QueryResponse(connector.getExchangeName(),
                        symbol, side, 0.0, 0, false, "Query to " + connector.getExchangeName() + " was cancelled"));
            } catch (ExecutionException e) {
                LOGGER.log(Level.WARNING, "Unexpected error querying " + connector.getExchangeName(), e);
                resultsByExchange.put(connector.getExchangeName(), new QueryResponse(connector.getExchangeName(),
                        symbol, side, 0.0, 0, false, "Unexpected error: " + e.getCause()));
            }
        }

        // Anything still pending here did not finish before the shared deadline.
        for (Map.Entry<Future<QueryResponse>, ExchangeConnector> entry : pending.entrySet()) {
            entry.getKey().cancel(true);
            ExchangeConnector connector = entry.getValue();
            // Interrupting the task's thread alone may not unblock a thread
            // stuck in a blocking socket read, so also force-close the
            // socket it is currently using, if any.
            connector.cancelPendingQuery();
            LOGGER.warning(() -> "Global query deadline expired waiting on " + connector.getExchangeName());
            resultsByExchange.put(connector.getExchangeName(), new QueryResponse(connector.getExchangeName(),
                    symbol, side, 0.0, 0, false, "Global query deadline expired waiting for " + connector.getExchangeName()));
        }

        // Preserve the configured exchange order so aggregation stays deterministic.
        List<QueryResponse> results = new ArrayList<>();
        for (ExchangeConnector connector : connectors) {
            results.add(resultsByExchange.get(connector.getExchangeName()));
        }
        return results;
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
