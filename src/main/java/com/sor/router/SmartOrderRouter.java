package com.sor.router;

import com.sor.common.config.RouterConfig;
import com.sor.common.util.LoggingConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for the Smart Order Router process. Acts as a TCP *server* to
 * Trading Clients (accepting orders) and, through ExecutionEngine, as a TCP
 * *client* to every configured Exchange Server (querying and executing).
 *
 * Usage: java com.sor.router.SmartOrderRouter <configSource>
 * where <configSource> is either a path to a .properties file or the name
 * of a bundled classpath resource, defaulting to "router" (router.properties).
 */
public class SmartOrderRouter {

    private static final Logger LOGGER = Logger.getLogger(SmartOrderRouter.class.getName());

    public static void main(String[] args) {
        LoggingConfig.init();
        String source = args.length > 0 ? args[0] : "router";

        try {
            RouterConfig config = RouterConfig.load(source);
            ExecutionEngine engine = new ExecutionEngine(config);
            ExecutorService clientPool = Executors.newCachedThreadPool();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Smart Order Router shutting down...");
                engine.shutdown();
                clientPool.shutdown();
            }));

            try (ServerSocket serverSocket = new ServerSocket(config.getPort())) {
                LOGGER.info(() -> "Smart Order Router listening on port " + config.getPort()
                        + " with exchanges: " + config.getExchanges());
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    clientPool.submit(new RouterClientHandler(clientSocket, engine));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Smart Order Router failed to start from source '" + source + "'", e);
            System.exit(1);
        }
    }
}
