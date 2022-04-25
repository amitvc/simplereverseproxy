# Simplereverseproxy
A proof of concept reverse proxy solution that allows you to have 1 or more
upstream servers with custom throttling rules. Currently, there is only one throttling
rule implementation.

### User guide
```java
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
```
Here you have configured ReverseProxy server listening on port 8000. This proxy is
fronting 2 upstream servers one of port 9000 and other on 9001. The appContext for the service is /app1 and /app2. 
What this means is you can get to these services via http://localhost:9001/app2 (direct) or http://localhost:9001/app2 (via proxy)

### Key points
- If you want to add custom throttling rules implement the ThrottleRule interface
- Currently, the ReverseProxy only supports http GET requests for the upstream servers
- If you wish to configure the throttle rule settings use the following url 
  `http://localhost:{proxy_port}/config?app={appContext}&time={value}`. This endpoint is specifically
   http post. Sample usage - `curl -X POST -L  "http://localhost:8000/config?app=app1&time=500"`
