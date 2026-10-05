package com.sor.client;

import com.sor.common.model.ExecutionReport;
import com.sor.common.model.Fill;
import com.sor.common.model.OrderRequest;
import com.sor.common.model.OrderSide;
import com.sor.common.protocol.ProtocolConstants;
import com.sor.common.util.LoggingConfig;

import java.io.IOException;
import java.util.Locale;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for a Trading Client: a small console UI that connects to the
 * Smart Order Router once, then lets a person type BUY/SELL orders and see
 * the resulting ExecutionReport, until they type EXIT.
 *
 * Usage: java com.sor.client.TradingClient [routerHost] [routerPort] [clientId]
 */
public class TradingClient {

    private static final Logger LOGGER = Logger.getLogger(TradingClient.class.getName());

    public static void main(String[] args) {
        LoggingConfig.init();

        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : ProtocolConstants.DEFAULT_ROUTER_PORT;
        String clientId = args.length > 2 ? args[2] : "CLIENT-" + (System.currentTimeMillis() % 100000);

        System.out.println("=== Smart Order Router - Trading Client (" + clientId + ") ===");
        System.out.println("Connecting to router at " + host + ":" + port + " ...");

        try (RouterConnector connector = new RouterConnector(host, port, ProtocolConstants.DEFAULT_CONNECT_TIMEOUT_MS);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected. Type HELP for commands.\n");

            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.equalsIgnoreCase("EXIT") || line.equalsIgnoreCase("QUIT")) {
                    System.out.println("Goodbye.");
                    break;
                }
                if (line.equalsIgnoreCase("HELP")) {
                    printHelp();
                    continue;
                }

                try {
                    OrderRequest request = parseOrder(line, clientId);
                    ExecutionReport report = connector.sendOrder(request);
                    printReport(report);
                } catch (IllegalArgumentException invalid) {
                    System.out.println("Invalid input: " + invalid.getMessage());
                    System.out.println("Type HELP for the expected command format.");
                } catch (IOException communicationError) {
                    System.out.println("Communication error talking to the router: " + communicationError.getMessage());
                    LOGGER.log(Level.WARNING, "Communication error", communicationError);
                    break;
                }
            }
        } catch (IOException connectError) {
            System.out.println("Could not connect to router at " + host + ":" + port + " - " + connectError.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to connect to router", connectError);
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  BUY <SYMBOL> <QUANTITY>   e.g. BUY AAPL 500");
        System.out.println("  SELL <SYMBOL> <QUANTITY>  e.g. SELL TSLA 200");
        System.out.println("  HELP                      show this message");
        System.out.println("  EXIT                      disconnect and quit");
    }

    private static OrderRequest parseOrder(String line, String clientId) {
        String[] tokens = line.split("\\s+");
        if (tokens.length != 3) {
            throw new IllegalArgumentException("expected: <BUY|SELL> <SYMBOL> <QUANTITY>");
        }

        OrderSide side;
        try {
            side = OrderSide.valueOf(tokens[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown side '" + tokens[0] + "', expected BUY or SELL");
        }

        String symbol = tokens[1].toUpperCase(Locale.ROOT);

        int quantity;
        try {
            quantity = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("quantity must be a whole number, got '" + tokens[2] + "'");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }

        return new OrderRequest(clientId, symbol, side, quantity);
    }

    private static void printReport(ExecutionReport report) {
        System.out.println();
        System.out.println("--- Execution Report ---");
        System.out.println("Order ID     : " + report.getOrderId());
        System.out.println("Symbol       : " + report.getSymbol() + " (" + report.getSide() + ")");
        System.out.println("Requested    : " + report.getRequestedQuantity());
        System.out.println("Executed     : " + report.getExecutedQuantity());
        System.out.println("Status       : " + report.getStatus());
        if (report.getFills() != null && !report.getFills().isEmpty()) {
            System.out.println("Fills        :");
            for (Fill fill : report.getFills()) {
                System.out.printf("  - %-10s qty=%-6d price=%.2f%n", fill.getExchangeName(), fill.getQuantity(), fill.getPrice());
            }
            System.out.printf("Average price: %.2f%n", report.getAveragePrice());
            System.out.printf("Total value  : %.2f%n", report.getTotalValue());
        }
        System.out.println("Message      : " + report.getMessage());
        System.out.println("-------------------------\n");
    }
}
