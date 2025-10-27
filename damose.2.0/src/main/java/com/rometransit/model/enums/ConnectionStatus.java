package com.rometransit.model.enums;

public enum ConnectionStatus {
    CONNECTING ("Connecting"),
    CONNECTED("Connected - Service ready"),
    ONLINE("Online - Real-time data available"),
    OFFLINE("Offline - Using cached data"),
    LIMITED("Limited - Partial connectivity"),
    ERROR("Error - Connection failed");

    private final String description;

    ConnectionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}