package com.rometransit.model.dto.dashboard;

/**
 * DTO per rappresentare i dati di affollamento di un veicolo/route.
 * Utilizzato per visualizzare le informazioni nella ListView del crowding.
 */
public class VehicleCrowdingData {
    private String routeId;
    private String routeName;
    private int occupancy;       // Passeggeri attuali
    private int capacity;        // Capacità massima
    private double percentage;   // occupancy/capacity * 100

    public VehicleCrowdingData() {
    }

    public VehicleCrowdingData(String routeId, String routeName, int occupancy, int capacity) {
        this.routeId = routeId;
        this.routeName = routeName;
        this.occupancy = occupancy;
        this.capacity = capacity;
        this.percentage = capacity > 0 ? (double) occupancy / capacity * 100 : 0;
    }

    /**
     * Restituisce il livello di affollamento basato sulla percentuale
     * @return LOW (<50%), MEDIUM (50-80%), HIGH (>80%)
     */
    public String getCrowdingLevel() {
        if (percentage < 50) return "LOW";
        if (percentage < 80) return "MEDIUM";
        return "HIGH";
    }

    /**
     * Testo da visualizzare per la route
     * @return "BUS {routeId} - {routeName}"
     */
    public String getDisplayText() {
        return String.format("BUS %s - %s", routeId, routeName);
    }

    /**
     * Testo da visualizzare per l'occupancy
     * @return "XX% (occupancy/capacity)"
     */
    public String getOccupancyText() {
        return String.format("%.0f%% (%d/%d)", percentage, occupancy, capacity);
    }

    // Getters e Setters

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public int getOccupancy() {
        return occupancy;
    }

    public void setOccupancy(int occupancy) {
        this.occupancy = occupancy;
        updatePercentage();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        updatePercentage();
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    private void updatePercentage() {
        this.percentage = capacity > 0 ? (double) occupancy / capacity * 100 : 0;
    }

    @Override
    public String toString() {
        return String.format("VehicleCrowdingData{routeId='%s', routeName='%s', occupancy=%d/%d (%.1f%%), level=%s}",
                routeId, routeName, occupancy, capacity, percentage, getCrowdingLevel());
    }
}
