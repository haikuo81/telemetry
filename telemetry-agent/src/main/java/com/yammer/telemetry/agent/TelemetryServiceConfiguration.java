package com.yammer.telemetry.agent;

public class TelemetryServiceConfiguration {
    private boolean enabled = false;
    private String host;
    private Integer port;

    public boolean isEnabled() {
        return enabled;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }
}
