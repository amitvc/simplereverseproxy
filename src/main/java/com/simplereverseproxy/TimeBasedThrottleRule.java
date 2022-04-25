package com.simplereverseproxy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;


public class TimeBasedThrottleRule implements ThrottleRule {

    private static final Logger logger = Logger.getLogger(TimeBasedThrottleRule.class.getSimpleName());

    private volatile  Map<Integer, RequestTimeStamp> requestStatsMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private volatile int timeBtwRequest;

    private static class RequestTimeStamp {
        private final long requestSeen;

        public static RequestTimeStamp instanceOf() {
            return new RequestTimeStamp(System.currentTimeMillis());
        }

        private RequestTimeStamp(long currentTimeMillis) {
            requestSeen = currentTimeMillis;
        }

        @Override
        public boolean equals(Object o) {
            final RequestTimeStamp that = (RequestTimeStamp) o;
            return requestSeen == that.requestSeen;
        }
    }

    public void updateTimeBetweenRequestParameter(int val) {
        this.timeBtwRequest = val;
    }

    @Override
    public boolean throttleRequest(HttpExchange request) {
        String path = request.getRequestURI().getPath();
        String query = request.getRequestURI().getQuery();
        int requestHash = computeHash(path, query);
        boolean dontThrottleRequest = false;
        if (requestStatsMap.containsKey(requestHash)) {
            RequestTimeStamp rst =  requestStatsMap.get(requestHash);
            if (System.currentTimeMillis() - rst.requestSeen > timeBtwRequest) {
                requestStatsMap.remove(requestHash);
                requestStatsMap.put(requestHash, RequestTimeStamp.instanceOf());
            } else {
                return true;
            }
        }
        requestStatsMap.put(requestHash, RequestTimeStamp.instanceOf());
        return dontThrottleRequest;
    }

    private int computeHash(String path, String queryParams) {
        StringBuilder sb = new StringBuilder();
        sb.append(path);
        if (queryParams != null) {
            String[] valuePairs = queryParams.split("&");
            TreeMap<String, String> queryParamMap = new TreeMap<>();
            for (String val : valuePairs) {
                String[] pair = val.split("=");
                queryParamMap.put(pair[0], pair[1]);
            }

            for(Map.Entry<String, String> entry : queryParamMap.entrySet()) {
                sb.append(entry.getKey());
                sb.append(entry.getValue());
            }
        }
        return sb.toString().hashCode();
    }

    public TimeBasedThrottleRule(int millis) {
        timeBtwRequest = millis;
        // We can expose this as a configuration as well.
        scheduledExecutorService.scheduleAtFixedRate(pruneRequestStatsMap(), 0, 5, TimeUnit.SECONDS);
        logger.info("Launching cache pruning thread at " + LocalDateTime.now());
    }

    public  Runnable pruneRequestStatsMap() {
        return () -> {
            Map<Integer, RequestTimeStamp> filteredRequests = new HashMap<>();
            for(Map.Entry<Integer, RequestTimeStamp> entry : requestStatsMap.entrySet()) {
                if (System.currentTimeMillis() - entry.getValue().requestSeen > timeBtwRequest) {
                    filteredRequests.put(entry.getKey(), entry.getValue());
                }
            }

            if (filteredRequests.size() > 0) {
                logger.info(String.format("%s : found %d entries that seems to have expired and need pruning.",
                                          LocalDateTime.now(), filteredRequests.size()));
            }

            // Now prune the entries that are expired.
            int count = 0;
            for (Map.Entry<Integer, RequestTimeStamp> entry : filteredRequests.entrySet()) {
                // We use remove(key,value) instead of remove(key). The reason is there a chance that
                // we have a new request come in from the time we have filtered expired requests and prune loop here.
                // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html#remove-java.lang.Object-java.lang.Object-
                requestStatsMap.remove(entry.getKey(), entry.getValue());
                count++;
            }

        };
    }
}
