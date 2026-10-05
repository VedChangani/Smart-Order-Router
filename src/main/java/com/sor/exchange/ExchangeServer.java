package com.sor.exchange;

import com.sor.common.config.AppConfig;
import com.sor.common.util.LoggingConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for one Exchange Server process. Each exchange is an
 * independent TCP server: it accepts connections (from the Smart Order
 * Router) on its own port and serves QUERY_REQUEST / EXECUTE_REQUEST
 * messages against its own in-memory Inventory.
 *
 * Usage: java com.sor.exchange.ExchangeServer <configSource>
 * where <configSource> is either a path to a .properties file or the name
 * of a bundled classpath resource such as "NYSE", "NASDAQ" or "BSE".
 */
public class ExchangeServer {

    private static final Logger LOGGER = Logger.getLogger(ExchangeServer.class.getName());

    public static void main(String[] args) {
        LoggingConfig.init();
        String source = args.length > 0 ? args[0] : "NYSE";

        try {
            AppConfig config = new AppConfig(source);
            String name = config.getRequiredString("exchange.name");
            int port = config.getInt("exchange.port", 7001);
            Inventory inventory = buildInventory(config);
            ExchangeService service = new ExchangeService(name, inventory);

            ExecutorService clientPool = Executors.newCachedThreadPool();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info(() -> name + " exchange shutting down...");
                clientPool.shutdown();
            }));

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                LOGGER.info(() -> name + " exchange listening on port " + port + " with symbols: "
                        + describeSymbols(inventory));
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    clientPool.submit(new ExchangeClientHandler(clientSocket, service));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Exchange server failed to start from source '" + source + "'", e);
            System.exit(1);
        }
    }

    private static Inventory buildInventory(AppConfig config) {
        Inventory inventory = new Inventory();
        String raw = config.getRequiredString("inventory");
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":");
            if (parts.length != 4) {
                throw new IllegalArgumentException(
                        "Invalid inventory entry '" + trimmed + "' (expected SYMBOL:price:available:capacity)");
            }
            String symbol = parts[0];
            double price = Double.parseDouble(parts[1]);
            int available = Integer.parseInt(parts[2]);
            int capacity = Integer.parseInt(parts[3]);
            inventory.addItem(new InventoryItem(symbol, price, available, capacity));
        }
        return inventory;
    }

    private static String describeSymbols(Inventory inventory) {
        StringBuilder sb = new StringBuilder();
        for (InventoryItem item : inventory.all()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(item.getSymbol()).append("@").append(item.getPrice());
        }
        return sb.toString();
    }
}
