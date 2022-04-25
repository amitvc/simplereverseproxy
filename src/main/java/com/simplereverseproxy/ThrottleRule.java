package com.simplereverseproxy;

import com.sun.net.httpserver.HttpExchange;

@FunctionalInterface
public interface ThrottleRule {
    boolean throttleRequest(HttpExchange request);
}
