package com.rometransit.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition.VehicleStopStatus;
import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.dto.ArrivalPrediction;
import com.rometransit.util.exception.DataException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for GTFS real-time Protocol Buffer data feeds
 * Handles vehicle positions and trip updates from Roma Mobilità
 */
public class GTFSRealtimeParser {

    public GTFSRealtimeParser() {
        System.out.println("🔧 GTFSRealtimeParser initialized for Protocol Buffers");
    }

    /**
     * Parse vehicle positions from GTFS Realtime Protocol Buffer data
     * @param protobufData Binary Protocol Buffer data
     * @return List of parsed vehicle positions
     */
    public List<VehiclePosition> parseVehiclePositions(byte[] protobufData) throws DataException {
        List<VehiclePosition> positions = new ArrayList<>();

        try {
            // Validate protobuf data before parsing
            if (protobufData == null || protobufData.length == 0) {
                System.out.println("⚠️ Empty protobuf data received - no vehicle positions available");
                return positions; // Return empty list instead of throwing exception
            }

            // Check for minimum valid protobuf size (FeedMessage with header is at least ~10 bytes)
            if (protobufData.length < 10) {
                System.out.println("⚠️ Protobuf data too small (" + protobufData.length + " bytes) - likely malformed");
                return positions;
            }

            InputStream inputStream = new ByteArrayInputStream(protobufData);
            FeedMessage feedMessage = FeedMessage.parseFrom(inputStream);

            // Verify FeedMessage has required header field
            if (!feedMessage.hasHeader()) {
                System.err.println("⚠️ FeedMessage missing required 'header' field - data is malformed");
                return positions; // Return empty list for malformed data
            }

            System.out.println("📦 Parsing GTFS-RT FeedMessage with " + feedMessage.getEntityCount() + " entities");

            for (FeedEntity entity : feedMessage.getEntityList()) {
                if (entity.hasVehicle()) {
                    try {
                        VehiclePosition position = parseVehicleEntity(entity);
                        if (position != null) {
                            positions.add(position);
                        }
                    } catch (Exception e) {
                        System.err.println("⚠️ Error parsing vehicle entity " + entity.getId() + ": " + e.getMessage());
                        // Continue parsing other entities
                    }
                }
            }

            System.out.println("✅ Parsed " + positions.size() + " vehicle positions");
            return positions;

        } catch (Exception e) {
            throw new DataException("Failed to parse vehicle positions protobuf", e);
        }
    }

    private VehiclePosition parseVehicleEntity(FeedEntity entity) {
        GtfsRealtime.VehiclePosition vehicleProto = entity.getVehicle();
        VehiclePosition position = new VehiclePosition();

        // Vehicle ID
        if (vehicleProto.hasVehicle() && vehicleProto.getVehicle().hasId()) {
            position.setVehicleId(vehicleProto.getVehicle().getId());
        } else if (entity.hasId()) {
            position.setVehicleId(entity.getId());
        }

        // Trip information
        if (vehicleProto.hasTrip()) {
            GtfsRealtime.TripDescriptor trip = vehicleProto.getTrip();

            if (trip.hasTripId()) {
                position.setTripId(trip.getTripId());
            }
            if (trip.hasRouteId()) {
                position.setRouteId(trip.getRouteId());
            }
            // DirectionId not used in VehiclePosition DTO
        }

        // Position data
        if (vehicleProto.hasPosition()) {
            GtfsRealtime.Position pos = vehicleProto.getPosition();

            if (pos.hasLatitude()) {
                position.setLatitude(pos.getLatitude());
            }
            if (pos.hasLongitude()) {
                position.setLongitude(pos.getLongitude());
            }
            if (pos.hasBearing()) {
                position.setBearing((double) pos.getBearing());
            }
            if (pos.hasSpeed()) {
                position.setSpeed((double) pos.getSpeed());
            }
        }

        // Timestamp
        if (vehicleProto.hasTimestamp()) {
            long timestamp = vehicleProto.getTimestamp();
            position.setTimestamp(timestamp);
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
            );
            position.setLastUpdate(dateTime);
        } else {
            position.setTimestamp(System.currentTimeMillis() / 1000);
            position.setLastUpdate(LocalDateTime.now());
        }

        // Current stop information
        if (vehicleProto.hasStopId()) {
            position.setCurrentStopId(vehicleProto.getStopId());
        }

        // Stop status - map protobuf status to VehicleStatus enum
        if (vehicleProto.hasCurrentStatus()) {
            VehicleStopStatus pbStatus = vehicleProto.getCurrentStatus();
            com.rometransit.model.enums.VehicleStatus status;
            switch (pbStatus) {
                case IN_TRANSIT_TO:
                    status = com.rometransit.model.enums.VehicleStatus.IN_TRANSIT_TO;
                    break;
                case STOPPED_AT:
                    status = com.rometransit.model.enums.VehicleStatus.STOPPED_AT;
                    break;
                case INCOMING_AT:
                    status = com.rometransit.model.enums.VehicleStatus.INCOMING_AT;
                    break;
                default:
                    status = com.rometransit.model.enums.VehicleStatus.UNKNOWN;
                    break;
            }
            position.setStatus(status);
        }

        // Occupancy Status - Extract passenger load information
        if (vehicleProto.hasOccupancyStatus()) {
            GtfsRealtime.VehiclePosition.OccupancyStatus occStatus = vehicleProto.getOccupancyStatus();

            // Map GTFS-RT OccupancyStatus to integer value
            // GTFS-RT OccupancyStatus values:
            // EMPTY = 0, MANY_SEATS_AVAILABLE = 1, FEW_SEATS_AVAILABLE = 2,
            // STANDING_ROOM_ONLY = 3, CRUSHED_STANDING_ROOM_ONLY = 4,
            // FULL = 5, NOT_ACCEPTING_PASSENGERS = 6
            int occupancyValue = 0;

            switch (occStatus) {
                case EMPTY:
                    occupancyValue = 0;  // 0% occupancy
                    break;
                case MANY_SEATS_AVAILABLE:
                    occupancyValue = 25; // ~25% occupancy
                    break;
                case FEW_SEATS_AVAILABLE:
                    occupancyValue = 50; // ~50% occupancy
                    break;
                case STANDING_ROOM_ONLY:
                    occupancyValue = 75; // ~75% occupancy
                    break;
                case CRUSHED_STANDING_ROOM_ONLY:
                    occupancyValue = 90; // ~90% occupancy
                    break;
                case FULL:
                    occupancyValue = 100; // 100% occupancy
                    break;
                case NOT_ACCEPTING_PASSENGERS:
                    occupancyValue = 100; // Treat as full
                    break;
                default:
                    occupancyValue = 0; // Unknown - treat as empty
                    break;
            }

            position.setOccupancyLevel(occupancyValue);
            System.out.println("📊 Vehicle " + position.getVehicleId() + " occupancy: " +
                             occStatus.name() + " (" + occupancyValue + "%)");
        }

        // Occupancy Percentage - If available, use exact percentage
        // Note: Some feeds provide occupancyPercentage field (0-100)
        // This is more accurate than the enum-based occupancyStatus
        if (vehicleProto.hasOccupancyPercentage()) {
            int exactOccupancy = vehicleProto.getOccupancyPercentage();
            position.setOccupancyLevel(exactOccupancy);
            System.out.println("📊 Vehicle " + position.getVehicleId() +
                             " exact occupancy: " + exactOccupancy + "%");
        }

        // Validate required fields
        if (position.getVehicleId() == null ||
            position.getLatitude() == 0.0 ||
            position.getLongitude() == 0.0) {
            return null; // Invalid position
        }

        return position;
    }

    /**
     * Parse trip updates from GTFS Realtime Protocol Buffer data
     * @param protobufData Binary Protocol Buffer data
     * @return List of parsed arrival predictions
     */
    public List<ArrivalPrediction> parseTripUpdates(byte[] protobufData) throws DataException {
        List<ArrivalPrediction> predictions = new ArrayList<>();

        try {
            // Validate protobuf data before parsing
            if (protobufData == null || protobufData.length == 0) {
                System.out.println("⚠️ Empty protobuf data received - no trip updates available");
                return predictions;
            }

            if (protobufData.length < 10) {
                System.out.println("⚠️ Protobuf data too small (" + protobufData.length + " bytes) - likely malformed");
                return predictions;
            }

            InputStream inputStream = new ByteArrayInputStream(protobufData);
            FeedMessage feedMessage = FeedMessage.parseFrom(inputStream);

            // Verify FeedMessage has required header field
            if (!feedMessage.hasHeader()) {
                System.err.println("⚠️ FeedMessage missing required 'header' field - data is malformed");
                return predictions;
            }

            System.out.println("📦 Parsing GTFS-RT TripUpdates with " + feedMessage.getEntityCount() + " entities");

            for (FeedEntity entity : feedMessage.getEntityList()) {
                if (entity.hasTripUpdate()) {
                    try {
                        List<ArrivalPrediction> entityPredictions = parseTripUpdateEntity(entity);
                        predictions.addAll(entityPredictions);
                    } catch (Exception e) {
                        System.err.println("⚠️ Error parsing trip update entity " + entity.getId() + ": " + e.getMessage());
                        // Continue parsing other entities
                    }
                }
            }

            System.out.println("✅ Parsed " + predictions.size() + " arrival predictions");
            return predictions;

        } catch (Exception e) {
            throw new DataException("Failed to parse trip updates protobuf", e);
        }
    }

    private List<ArrivalPrediction> parseTripUpdateEntity(FeedEntity entity) {
        List<ArrivalPrediction> predictions = new ArrayList<>();
        GtfsRealtime.TripUpdate tripUpdate = entity.getTripUpdate();

        // Get trip information
        String tripId = null;
        String routeId = null;

        if (tripUpdate.hasTrip()) {
            GtfsRealtime.TripDescriptor trip = tripUpdate.getTrip();
            tripId = trip.hasTripId() ? trip.getTripId() : null;
            routeId = trip.hasRouteId() ? trip.getRouteId() : null;
        }

        // Process stop time updates
        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
            try {
                ArrivalPrediction prediction = parseStopTimeUpdate(stopTimeUpdate, tripId, routeId);
                if (prediction != null) {
                    predictions.add(prediction);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error parsing stop time update: " + e.getMessage());
            }
        }

        return predictions;
    }

    private ArrivalPrediction parseStopTimeUpdate(
            GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate,
            String tripId,
            String routeId) {

        ArrivalPrediction prediction = new ArrivalPrediction();

        // Stop information
        if (stopTimeUpdate.hasStopId()) {
            prediction.setStopId(stopTimeUpdate.getStopId());
        }

        // Trip and route IDs
        prediction.setTripId(tripId);
        prediction.setRouteId(routeId);

        // Arrival time
        if (stopTimeUpdate.hasArrival()) {
            GtfsRealtime.TripUpdate.StopTimeEvent arrival = stopTimeUpdate.getArrival();

            if (arrival.hasTime()) {
                long timestamp = arrival.getTime();
                prediction.setArrivalTime(timestamp);
                LocalDateTime arrivalTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp),
                    ZoneId.systemDefault()
                );
                prediction.setPredictedArrival(arrivalTime);
                prediction.setRealtime(true);
            }

            if (arrival.hasDelay()) {
                int delaySeconds = arrival.getDelay();
                prediction.setDelay(delaySeconds);
                prediction.setDelayMinutes(delaySeconds / 60);
            }
        }

        // Departure time
        if (stopTimeUpdate.hasDeparture()) {
            GtfsRealtime.TripUpdate.StopTimeEvent departure = stopTimeUpdate.getDeparture();

            if (departure.hasTime()) {
                long timestamp = departure.getTime();
                prediction.setDepartureTime(timestamp);
            }
        }

        // Set prediction time to now
        prediction.setPredictionTime(LocalDateTime.now());

        // Validate required fields
        if (prediction.getStopId() == null || prediction.getPredictedArrival() == null) {
            return null;
        }

        return prediction;
    }

    /**
     * Parse vehicle positions from legacy JSON format (fallback)
     * Kept for backward compatibility
     */
    @Deprecated
    public List<VehiclePosition> parseVehiclePositionsJson(String jsonData) throws DataException {
        throw new DataException("JSON format no longer supported. Use Protocol Buffers format (.pb files)");
    }

    /**
     * Parse trip updates from legacy JSON format (fallback)
     * Kept for backward compatibility
     */
    @Deprecated
    public List<ArrivalPrediction> parseTripUpdatesJson(String jsonData) throws DataException {
        throw new DataException("JSON format no longer supported. Use Protocol Buffers format (.pb files)");
    }
}
