package com.simplereverseproxy;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration rules for each upstream server you may have behind the proxy.
 */
public final class RouteConfig {
    private String appContext;
    private String hostUrl;
    private Set<ThrottleRule> throttleRules;

    public Set<ThrottleRule> getThrottleRules() {
        return throttleRules;
    }

    public String getAppContext() {
        return appContext;
    }

    public String getHostUrl() {
        return hostUrl;
    }

    private RouteConfig(Builder b) {
        appContext = b.appContext;
        hostUrl = b.hostUrl;
        throttleRules = b.rules;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public String appContext;
        public String hostUrl;
        private Set<ThrottleRule> rules;

        public Builder appContext(String appContext) {
            this.appContext = appContext;
            return this;
        }

        public Builder hostUrl(String hostUrl) {
            this.hostUrl = hostUrl;
            return this;
        }

        public Builder throttleRule(ThrottleRule rule) {
            if (rules == null) {
                rules = new HashSet<>();
            }
            rules.add(rule);
            return this;
        }

        public RouteConfig build() {
            return new RouteConfig(this);
        }
    }
}
