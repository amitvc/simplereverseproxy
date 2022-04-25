package com.simplereverseproxy;

public class TestProxyLibrary {

    public static void main(String[] args) {
        final Server proxyServer = ReverseProxyServer.builder()
                                                     .hostName("localhost").port(8000)
                                                     .routeConfig(RouteConfig.builder().appContext("app1")
                                                                             .hostUrl(
                                                                                     "http://localhost:9000")
                                                                             .throttleRule(
                                                                                     new TimeBasedThrottleRule(
                                                                                             5000)).build())
                                                     .routeConfig(RouteConfig.builder().appContext("app2")
                                                                             .hostUrl(
                                                                                     "http://localhost:9001")
                                                                             .throttleRule(
                                                                                     new TimeBasedThrottleRule(
                                                                                             5000))
                                                                             .build()).build();
        proxyServer.start();
        System.out.println("Proxy server started");
    }
}
