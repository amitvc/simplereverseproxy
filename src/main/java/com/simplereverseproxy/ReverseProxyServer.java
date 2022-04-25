package com.simplereverseproxy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.simplereverseproxy.exceptions.RouteConfigurationNotFoundException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Class acts as a reverse proxy server routing requests its receives to the configured
 * backend servers.
 */
public final class ReverseProxyServer implements Server {

    private static final Logger logger = Logger.getLogger(ReverseProxyServer.class.getSimpleName());

    private int threadPoolSize;
    private int port;
    private String hostName;
    private int requestTimeout;
    private HttpServer server;
    private RequestRouter requestRouter;
    private ReverseProxyServer(Builder builder) {
        threadPoolSize = builder.threadPoolSize;
        requestTimeout = 1000;
        port = builder.port;
        hostName = builder.hostName;
        requestRouter = new RequestRouter(builder.routeConfigs);
        if (port == 0 || Objects.isNull(hostName)) {
            throw new IllegalStateException("Proxy server is setup correctly. Please setup port and hostname");
        }

    }

    public static final class Builder {
        private int threadPoolSize = Runtime.getRuntime().availableProcessors();
        private int port;
        private String hostName;
        private int requestTimeout;
        private Set<RouteConfig> routeConfigs;

        public Builder() {
            // Default thread pool size
            threadPoolSize = Runtime.getRuntime().availableProcessors();
            requestTimeout = 1000; // Default request timeout
        }

        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder hostName(String hostName) {
            this.hostName = hostName;
            return this;
        }

        public Builder requestTimeout(int val) {
            requestTimeout = val;
            return this;
        }

        public Builder routeConfig(RouteConfig config) {
            if (routeConfigs == null) {
                routeConfigs = new HashSet<>();
            }
            routeConfigs.add(config);
            return this;
        }

        public ReverseProxyServer build() {
            return new ReverseProxyServer(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        try {
            logger.info("Starting proxy server at port " + port);
            server = HttpServer.create(new InetSocketAddress(hostName, port), 0); // backlog=0 uses default system value.
            server.setExecutor(Executors.newFixedThreadPool(threadPoolSize));
            // Create config endpoint that is used to configure the throttling rate limiter configuration
            server.createContext("/config", routingConfigUpdateHandler());
            server.createContext("/", requestRouter);
            server.start();
        } catch (IOException e) {
            logger.severe(String.format("Problem creating proxy server at port %d %s", port, e.getMessage()));
        }
    }

    @Override
    public void shutdown() {

    }

    public HttpHandler routingConfigUpdateHandler() {
        return (HttpExchange request) -> {
            if (request.getRequestMethod().equalsIgnoreCase("POST") && request.getRequestURI().getPath().equals("/config")) {
                String[] queryParams = request.getRequestURI().getQuery().split("&");
                String appName = extractQueryValue(queryParams, "app");
                int timeBtwRequest = Integer.parseInt(extractQueryValue(queryParams, "time"));
                try {
                    requestRouter.updateTimeBasedThrottlingRuleParameter(appName, timeBtwRequest);
                    String response = "Update successfully applied";
                    // Notify client successful update.
                    request.sendResponseHeaders(200, response.length());
                    OutputStream os = request.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } catch (RouteConfigurationNotFoundException ex) {
                    // we did not find the route configured for the app supplied by the user.
                    logger.warning(String.format("Route for app %s not configure", appName));
                    String response = "Requested route not configured. Please check the configuration";
                    request.sendResponseHeaders( 404, response.length());
                    OutputStream os = request.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }
        };
    }

    public static String extractQueryValue(String [] params, String key) {
        for (String query: params) {
            String[] values = query.split("=");
            if (values.length != 2) {
                throw new IllegalArgumentException("Illegal arguments passed");
            }
            if (key.equals(values[0])) {
                return values[1].trim();
            }
        }
        // If we are here then throw an exception since we did not find the query parameter
        throw new IllegalStateException(String.format("Provided key %s is not part of the query parameter", key));
    }
}
