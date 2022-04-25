package com.simplereverseproxy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.simplereverseproxy.exceptions.RouteConfigurationNotFoundException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RequestRouter implements HttpHandler {
    private static final Logger logger = Logger.getLogger(RequestRouter.class.getSimpleName());

    private final Map<String, RouteConfig> routeMap;
    private final int requestTimeout;

    public RequestRouter(Set<RouteConfig> configs, int requestTimeout) {
        routeMap = new HashMap<>();
        for (RouteConfig config : configs) {
            routeMap.put(config.getAppContext(), config);
        }
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void handle(HttpExchange request) throws IOException {
        if (request.getRequestMethod().equals("GET")) {
            String routeContext = extractRouteContext(request.getRequestURI().getPath());
            if (!routeMap.containsKey(routeContext)) {
                logger.warning(String.format("Route for app %s not configure", routeContext));
                String response = "Requested route not configured. Please check the configuration";
                request.sendResponseHeaders( 404, response.length());
                OutputStream os = request.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                try {
                    routeRequest(request, routeContext);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            logger.warning("We are currently only handling GET traffic");
            String response = "We are currently only handling GET traffic";
            request.sendResponseHeaders( 405, response.length());
            OutputStream os = request.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private void routeRequest(HttpExchange request, String routeContext) throws Exception {
        RouteConfig routeConfig = routeMap.get(routeContext);
        boolean requestThrottled = false;
        if (routeConfig != null) {
            String upstreamServerUrl = routeConfig.getHostUrl();
            for (ThrottleRule throttleRule : routeConfig.getThrottleRules()) {
                if (throttleRule.throttleRequest(request)) {
                    logger.info("Request is being throttled due to " + throttleRule.getClass().getSimpleName() + " rule");
                    String response = "Too many requests. Try again later";
                    request.sendResponseHeaders( 404, response.length());
                    OutputStream os = request.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    requestThrottled = true;
                }
            }

            if (!requestThrottled) {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                                                     .uri(buildUpstreamServerURI(routeConfig.getHostUrl(), request))
                                                     .GET().timeout(Duration.ofMillis(requestTimeout)).build();
                HttpClient client = HttpClient.newHttpClient(); // This can be reused. Change later.
                HttpResponse<byte[]> response = client.send(httpRequest, BodyHandlers.ofByteArray());
                byte[] upstreamServerResponse = response.body();
                request.sendResponseHeaders(response.statusCode(), upstreamServerResponse.length);
                OutputStream os = request.getResponseBody();
                os.write(upstreamServerResponse);
                os.close();
            }
        }
    }

    private static URI buildUpstreamServerURI(String hostnameUrl, HttpExchange request)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(hostnameUrl);
        String path =  request.getRequestURI().getPath();
        String query = request.getRequestURI().getQuery();
        sb.append(path);
        if (query != null) {
            sb.append("?");
            sb.append(query);
        }

        return new URI(sb.toString());
    }

    private static String extractRouteContext(String path) {
        return path.indexOf("/", 1) == -1 ?  path.substring(1) : path.substring(1, path.indexOf("/", 1));
    }

    public void updateTimeBasedThrottlingRuleParameter(String appName, int timeBtwRequest) {
        if (routeMap.containsKey(appName)) {
            Set<ThrottleRule> throttleRuleSet = routeMap.get(appName).getThrottleRules();
            if (throttleRuleSet != null) {
                for (ThrottleRule r : throttleRuleSet) {
                    if (r instanceof TimeBasedThrottleRule) {
                        ((TimeBasedThrottleRule)r).updateTimeBetweenRequestParameter(timeBtwRequest);
                    }
                }
            }
        } else {
            throw new RouteConfigurationNotFoundException(String.format("Route for app %s not configure", appName));
        }
    }
}
