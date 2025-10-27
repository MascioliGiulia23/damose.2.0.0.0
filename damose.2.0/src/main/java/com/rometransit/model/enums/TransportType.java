package com.rometransit.model.enums;

public enum TransportType {
    BUS("Bus", 0),
    TRAM("Tram", 1),
    METRO("Metro", 2),
    TRAIN("Train", 3),
    FERRY("Ferry", 4);

    private final String displayName;
    private final int gtfsType;

    TransportType(String displayName, int gtfsType) {
        this.displayName = displayName;
        this.gtfsType = gtfsType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getGtfsType() {
        return gtfsType;
    }

    // Alias method for compatibility
    public int getGtfsCode() {
        return gtfsType;
    }

    public static TransportType fromGtfsType(int gtfsType) {
        for (TransportType type : values()) {
            if (type.gtfsType == gtfsType) {
                return type;
            }
        }
        return BUS;
    }

    // Alias method for compatibility
    public static TransportType fromGtfsCode(int gtfsCode) {
        return fromGtfsType(gtfsCode);
    }
}