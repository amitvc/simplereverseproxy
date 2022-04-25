package com.simplereverseproxy.exceptions;

public class RouteConfigurationNotFoundException extends RuntimeException {
    public RouteConfigurationNotFoundException(String format) {
        super(format);
    }
}
