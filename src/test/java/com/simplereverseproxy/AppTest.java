package com.simplereverseproxy;

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.sun.net.httpserver.HttpServer;

/**
 * Unit test for simple App.
 */
public class AppTest {

    private static final Logger logger = Logger.getLogger(AppTest.class.getSimpleName());

    private static HttpServer service1, service2;


    @BeforeClass
    public static void setup() throws IOException {
        logger.info("Setting up backstream servers");
        service1 = HttpServer.create(new InetSocketAddress("localhost", 9000), 0);
        service1.createContext("/app1", (req) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Response from service 1.");
            sb.append("Query : " + req.getRequestURI().getQuery());
            req.sendResponseHeaders(200, sb.length());
            OutputStream os = req.getResponseBody();
            os.write(sb.toString().getBytes());
            os.close();
        });
        service1.start();

        service2 = HttpServer.create(new InetSocketAddress("localhost", 9001), 0);
        service2.createContext("/app2", (req) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Response from service 2.");
            sb.append("Query : " + req.getRequestURI().getQuery());
            req.sendResponseHeaders(200, sb.length());
            OutputStream os = req.getResponseBody();
            os.write(sb.toString().getBytes());
            os.close();
        });
        service2.start();
    }

    @AfterClass
    public static void shutdown() {
        logger.info("shutting down backstream servers");
        service1.stop(0);
        service2.stop(0);
    }

    @Test
    public void testWithSingleUpstreamServer() throws InterruptedException, URISyntaxException, IOException {
        int timeBtwRequest = 1000;
        final Server proxyServer = ReverseProxyServer.builder()
                                                     .hostName("localhost").port(8000)
                                                     .routeConfig(RouteConfig.builder().appContext("app1")
                                                                             .hostUrl(
                                                                                     "http://localhost:9000")
                                                                             .throttleRule(
                                                                                     new TimeBasedThrottleRule(
                                                                                             timeBtwRequest)).build())
                                                     .build();
        proxyServer.start();

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                                             .uri(new URI("http://localhost:8000/app1"))
                                             .GET().build();

        HttpResponse<byte[]> response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 200);
        assertTrue(new String(response.body()).contains("Response from service 1."));

        // Call again and the request will be throttled since it is canonical identical
        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 404);
        assertTrue(new String(response.body()).contains("Too many requests. Try again later"));

        Thread.sleep(1000); // Pass the throttle threshold and try again
        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 200);
        assertTrue(new String(response.body()).contains("Response from service 1."));

        // Test we don't throttle if requests are not canonical identical
        for (int i=0; i < 20; i++) {
            httpRequest = HttpRequest.newBuilder()
                       .uri(new URI("http://localhost:8000/app1/"+ i+1)) // simulating canonical different urls
                       .GET().build();
            response = client.send(httpRequest, BodyHandlers.ofByteArray());
            assertTrue(response.statusCode() == 200);
            assertTrue(new String(response.body()).contains("Response from service 1."));
        }


        // Do all these requests again and they should be throttled
        for (int i=0; i < 20; i++) {
            httpRequest = HttpRequest.newBuilder()
                                     .uri(new URI("http://localhost:8000/app1/"+ i+1))
                                     .GET().build();
            response = client.send(httpRequest, BodyHandlers.ofByteArray());
            assertTrue(response.statusCode() == 404);
            assertTrue(new String(response.body()).contains("Too many requests. Try again later"));
        }

        proxyServer.shutdown();
    }

    @Test
    public void testWithMultipleUpstreamServers() throws InterruptedException, URISyntaxException, IOException {
        int timeBtwRequest = 5000;
        final Server proxyServer = ReverseProxyServer.builder()
                                                     .hostName("localhost").port(8000)
                                                     .routeConfig(RouteConfig.builder().appContext("app1")
                                                                             .hostUrl(
                                                                                     "http://localhost:9000")
                                                                             .throttleRule(
                                                                                     new TimeBasedThrottleRule(
                                                                                             timeBtwRequest)).build())
                                                     .routeConfig(RouteConfig.builder().appContext("app2")
                                                                             .hostUrl(
                                                                                     "http://localhost:9001")
                                                                             .throttleRule(
                                                                                     new TimeBasedThrottleRule(
                                                                                             timeBtwRequest)).build())
                                                     .build();
        proxyServer.start();

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                                             .uri(new URI("http://localhost:8000/app1/test?q=100&z=200"))
                                             .GET().build();

        HttpResponse<byte[]> response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 200);
        assertTrue(new String(response.body()).contains("Response from service 1."));

        // This request is canonically identical even though the query params order is switched.
        httpRequest = HttpRequest.newBuilder()
                                 .uri(new URI("http://localhost:8000/app1/test?z=200&q=100"))
                                 .GET().build();
        // Call again and the request will be throttled since it is canonical identical
        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 404);
        assertTrue(new String(response.body()).contains("Too many requests. Try again later"));

        Thread.sleep(5000);
        httpRequest = HttpRequest.newBuilder()
                                 .uri(new URI("http://localhost:8000/app1/test?q=100&z=200"))
                                 .GET().build();

        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 200);
        assertTrue(new String(response.body()).contains("Response from service 1."));

        // Now lets call service 2.
        httpRequest = HttpRequest.newBuilder()
                                 .uri(new URI("http://localhost:8000/app2/test?q=100&z=200"))
                                 .GET().build();

        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 200);
        assertTrue(new String(response.body()).contains("Response from service 2."));



        httpRequest = HttpRequest.newBuilder()
                                 .uri(new URI("http://localhost:8000/app2/test?q=100&z=200"))
                                 .GET().build();


        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 404);
        assertTrue(new String(response.body()).contains("Too many requests. Try again later"));

        httpRequest = HttpRequest.newBuilder()
                                 .uri(new URI("http://localhost:8000/config?app=app2&time=1200"))
                                 .POST(BodyPublishers.noBody()).build();
        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 200);
        assertTrue(new String(response.body()).contains("Update successfully applied"));


        Thread.sleep(1200); // Reduce the timeInBetween value to 1200 ms from 5000 ms

        // Do the request again and it should not be throttled.
        httpRequest = HttpRequest.newBuilder()
                                 .uri(new URI("http://localhost:8000/app2/test?q=100&z=200"))
                                 .GET().build();


        response = client.send(httpRequest, BodyHandlers.ofByteArray());
        assertTrue(response.statusCode() == 200);
        assertTrue(new String(response.body()).contains("Response from service 2."));

        proxyServer.shutdown();
    }

    @Test
    public void testPruningRequestCacheWorks() throws Exception {
        int timeBtwRequest = 5000;
        final Server proxyServer = ReverseProxyServer.builder()
                                                     .hostName("localhost").port(8000)
                                                     .routeConfig(RouteConfig.builder().appContext("app1")
                                                                             .hostUrl(
                                                                                     "http://localhost:9000")
                                                                             .throttleRule(
                                                                                     new TimeBasedThrottleRule(
                                                                                             timeBtwRequest)).build())
                                                     .build();
        proxyServer.start();

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest httpRequest;

        HttpResponse<byte[]> response;

        // First time request and they don't throttled
        for (int i=0; i < 20; i++) {
            httpRequest = HttpRequest.newBuilder()
                                     .uri(new URI("http://localhost:8000/app1/"+ i+1)) // simulating canonical different urls
                                     .GET().build();
            response = client.send(httpRequest, BodyHandlers.ofByteArray());
            assertTrue(response.statusCode() == 200);
            assertTrue(new String(response.body()).contains("Response from service 1."));
        }

        Thread.sleep(500);
        // Do all these requests again and they should be throttled
        for (int i=0; i < 20; i++) {
            httpRequest = HttpRequest.newBuilder()
                                     .uri(new URI("http://localhost:8000/app1/"+ i+1))
                                     .GET().build();
            response = client.send(httpRequest, BodyHandlers.ofByteArray());
            assertTrue(response.statusCode() == 404);
            assertTrue(new String(response.body()).contains("Too many requests. Try again later"));
        }

        // Sleep for another 5 seconds. During this time the pruning thread will have pruned expired entries.
        // Look for message like this  found {%d} entries that seems to have expired and need pruning.
        Thread.sleep(5000);

        for (int i=0; i < 20; i++) {
            httpRequest = HttpRequest.newBuilder()
                                     .uri(new URI("http://localhost:8000/app1/"+ i+1)) // simulating canonical different urls
                                     .GET().build();
            response = client.send(httpRequest, BodyHandlers.ofByteArray());
            assertTrue(response.statusCode() == 200);
            assertTrue(new String(response.body()).contains("Response from service 1."));
        }
        proxyServer.shutdown();
    }


}
