package com.rometransit.model.enums;

public enum VehicleStatus {
    INCOMING_AT("Incoming at", 0),
    STOPPED_AT("Stopped at", 1),
    IN_TRANSIT_TO("In transit to", 2),
    UNKNOWN("Unknown", 3);

    private final String description;
    private final int gtfsStatus;

    VehicleStatus(String description, int gtfsStatus) {
        this.description = description;
        this.gtfsStatus = gtfsStatus;
    }

    public String getDescription() {
        return description;
    }

    public int getGtfsStatus() {
        return gtfsStatus;
    }

    public static VehicleStatus fromGtfsStatus(int gtfsStatus) {
        for (VehicleStatus status : values()) {
            if (status.gtfsStatus == gtfsStatus) {
                return status;
            }
        }
        return UNKNOWN;
    }
}