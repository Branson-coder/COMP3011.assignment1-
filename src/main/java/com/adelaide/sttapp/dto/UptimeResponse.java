package com.adelaide.sttapp.dto;

/**
 * Matches the YAML spec's UptimeResponse schema exactly (additionalProperties: false):
 * utcServerStart, utcNow, serverUptimeSeconds - no more, no less.
 */
public class UptimeResponse {

    private final String utcServerStart;
    private final String utcNow;
    private final double serverUptimeSeconds;

    public UptimeResponse(String utcServerStart, String utcNow, double serverUptimeSeconds) {
        this.utcServerStart = utcServerStart;
        this.utcNow = utcNow;
        this.serverUptimeSeconds = serverUptimeSeconds;
    }

    public String getUtcServerStart() {
        return utcServerStart;
    }

    public String getUtcNow() {
        return utcNow;
    }

    public double getServerUptimeSeconds() {
        return serverUptimeSeconds;
    }
}
