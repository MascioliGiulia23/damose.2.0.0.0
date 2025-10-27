package com.rometransit.service.realtime;

import com.rometransit.model.converter.VehiclePositionConverter;
import com.rometransit.data.repository.VehicleRepository;
import com.rometransit.data.repository.IncidentRepository;
import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.dto.ArrivalPrediction;
import com.rometransit.model.entity.Vehicle;
import com.rometransit.service.gtfs.GTFSOnlineDataService;
import com.rometransit.service.gtfs.GTFSRealtimeParser;
import com.rometransit.util.logging.Logger;
import com.rometransit.data.repository.GTFSRepository;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time Data Synchronization Service
 *
 * Orchestrates the complete flow of real-time data:
 * 1. Downloads GTFS real-time feeds (vehicle positions, trip updates)
 * 2. Parses protobuf data
 * 3. Converts DTOs to entities
 * 4. Saves to database
 * 5. Handles errors with cache fallback
 *
 * This service runs in background and updates the database every 30 seconds.
 * The dashboard automatically receives fresh data through existing repositories.
 */
public class RealtimeDataSyncService {

    private static final Logger logger = Logger.getLogger(RealtimeDataSyncService.class);

    // Default configuration
    private static final int DEFAULT_SYNC_INTERVAL_SECONDS = 30;
    private static final int RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 5000;
    private static final int STALE_DATA_THRESHOLD_MINUTES = 10;

    // Services
    private final GTFSOnlineDataService onlineDataService;
    private final GTFSRealtimeParser realtimeParser;
    private final GTFSRepository gtfsRepository;
    private final VehicleRepository vehicleRepository;
    private final IncidentRepository incidentRepository;

    // Converters
    private final VehiclePositionConverter vehicleConverter;
    private final IncidentSyncService incidentSyncService;

    // Scheduling
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isUsingCache = new AtomicBoolean(false);

    // Metrics
    private final AtomicLong totalSyncCycles = new AtomicLong(0);
    private final AtomicLong successfulSyncs = new AtomicLong(0);
    private final AtomicLong failedSyncs = new AtomicLong(0);
    private volatile LocalDateTime lastSuccessfulSync;
    private volatile LocalDateTime lastAttemptedSync;
    private volatile int currentVehicleCount = 0;
    private volatile int currentIncidentCount = 0;

    public RealtimeDataSyncService() {
        logger.info("Initializing RealtimeDataSyncService...");

        // Initialize services
        this.onlineDataService = new GTFSOnlineDataService();
        this.realtimeParser = new GTFSRealtimeParser();
        this.gtfsRepository = GTFSRepository.getInstance();
        this.vehicleRepository = new VehicleRepository();
        this.incidentRepository = new IncidentRepository();

        // Initialize converters
        this.vehicleConverter = new VehiclePositionConverter();
        this.incidentSyncService = new IncidentSyncService(incidentRepository);

        logger.info("RealtimeDataSyncService initialized successfully");
    }

    /**
     * Start real-time synchronization with default interval (30 seconds)
     */
    public void startSync() {
        startSync(DEFAULT_SYNC_INTERVAL_SECONDS);
    }

    /**
     * Start real-time synchronization with custom interval
     * @param intervalSeconds Sync interval in seconds
     */
    public void startSync(int intervalSeconds) {
        if (isRunning.get()) {
            logger.warn("Sync service already running, ignoring start request");
            return;
        }

        logger.info("Starting real-time sync service (interval: " + intervalSeconds + "s)");

        isRunning.set(true);

        // Create scheduler
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "RealtimeSyncThread");
            thread.setDaemon(true);
            return thread;
        });

        // Run initial sync immediately
        logger.info("Running initial sync...");
        executeSyncCycle();

        // Schedule periodic sync
        scheduler.scheduleAtFixedRate(
            this::executeSyncCycle,
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS
        );

        logger.info("Real-time sync service started successfully");
    }

    /**
     * Stop real-time synchronization
     */
    public void stopSync() {
        if (!isRunning.get()) {
            logger.warn("Sync service not running, ignoring stop request");
            return;
        }

        logger.info("Stopping real-time sync service...");

        isRunning.set(false);

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Real-time sync service stopped");
    }

    /**
     * Execute one complete sync cycle
     */
    private void executeSyncCycle() {
        lastAttemptedSync = LocalDateTime.now();
        totalSyncCycles.incrementAndGet();

        logger.info("=== Starting sync cycle #" + totalSyncCycles.get() + " ===");

        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            // Sync vehicle positions
            syncVehiclePositions();

            // Sync trip updates and detect incidents
            syncTripUpdates();

            // Cleanup stale data
            cleanupStaleData();

            // Mark as successful
            success = true;
            successfulSyncs.incrementAndGet();
            lastSuccessfulSync = LocalDateTime.now();
            isUsingCache.set(false);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Sync cycle completed successfully (duration: " + duration + "ms)");
            logger.info("Current state: " + currentVehicleCount + " vehicles, " +
                       currentIncidentCount + " incidents");

        } catch (Exception e) {
            success = false;
            failedSyncs.incrementAndGet();
            handleSyncFailure(e);
        }

        logger.info("=== Sync cycle #" + totalSyncCycles.get() + " " +
                   (success ? "COMPLETED" : "FAILED") + " ===");
    }

    /**
     * Sync vehicle positions from GTFS real-time feed
     */
    private void syncVehiclePositions() {
        logger.info("Syncing vehicle positions...");

        List<VehiclePosition> positions = null;

        try {
            // Download vehicle positions (protobuf)
            byte[] protobufData = onlineDataService.downloadVehiclePositions();

            if (protobufData == null || protobufData.length == 0) {
                logger.warn("No vehicle position data available (normal outside operating hours)");
                // Try to use cached data
                positions = gtfsRepository.loadVehiclePositions();
                if (!positions.isEmpty()) {
                    isUsingCache.set(true);
                    logger.info("Using cached vehicle positions (" + positions.size() + " vehicles)");
                }
            } else {
                // Parse protobuf
                positions = realtimeParser.parseVehiclePositions(protobufData);
                logger.info("Parsed " + positions.size() + " vehicle positions from real-time feed");

                // Cache for fallback
                if (!positions.isEmpty()) {
                    gtfsRepository.saveVehiclePositions(positions);
                }
            }

        } catch (Exception e) {
            logger.error("Error downloading/parsing vehicle positions: " + e.getMessage());

            // Fallback to cache
            try {
                positions = gtfsRepository.loadVehiclePositions();
                if (!positions.isEmpty()) {
                    isUsingCache.set(true);
                    logger.info("Fallback: Using cached vehicle positions (" + positions.size() + " vehicles)");
                } else {
                    throw new RuntimeException("No vehicle data available (live or cached)", e);
                }
            } catch (java.sql.SQLException ex) {
                throw new RuntimeException("Failed to load cached vehicle positions", ex);
            }
        }

        // Save to database
        if (positions != null && !positions.isEmpty()) {
            saveVehiclesToDatabase(positions);
        } else {
            logger.warn("No vehicle positions to save");
            currentVehicleCount = 0;
        }
    }

    /**
     * Sync trip updates and detect incidents from delays
     */
    private void syncTripUpdates() {
        logger.info("Syncing trip updates...");

        List<ArrivalPrediction> predictions = null;

        try {
            // Download trip updates (protobuf)
            byte[] protobufData = onlineDataService.downloadTripUpdates();

            if (protobufData == null || protobufData.length == 0) {
                logger.warn("No trip update data available (normal outside operating hours)");
                // Try to use cached data
                predictions = java.util.Collections.emptyList(); // Load from database if needed
                if (!predictions.isEmpty()) {
                    isUsingCache.set(true);
                    logger.info("Using cached trip updates (" + predictions.size() + " predictions)");
                }
            } else {
                // Parse protobuf
                predictions = realtimeParser.parseTripUpdates(protobufData);
                logger.info("Parsed " + predictions.size() + " trip updates from real-time feed");

                // Cache for fallback
                if (!predictions.isEmpty()) {
                    gtfsRepository.saveTripUpdates(predictions);
                }
            }

        } catch (Exception e) {
            logger.error("Error downloading/parsing trip updates: " + e.getMessage());

            // Fallback to cache (empty for now)
            predictions = java.util.Collections.emptyList();
            if (!predictions.isEmpty()) {
                isUsingCache.set(true);
                logger.info("Fallback: Using cached trip updates (" + predictions.size() + " predictions)");
            } else {
                // Non-critical error, continue without trip updates
                logger.warn("No trip update data available (live or cached)");
            }
        }

        // Rileva e salva ritardi dai trip updates
        if (predictions != null && !predictions.isEmpty()) {
            int delaysDetected = incidentSyncService.syncIncidentsFromTripUpdates(predictions);
            currentIncidentCount = delaysDetected;
            logger.info("Rilevati/aggiornati " + delaysDetected + " ritardi dai trip updates");
        }
    }

    /**
     * Save vehicle positions to database
     */
    private void saveVehiclesToDatabase(List<VehiclePosition> positions) {
        logger.info("Saving " + positions.size() + " vehicles to database...");

        int saved = 0;
        int updated = 0;
        int errors = 0;

        for (VehiclePosition position : positions) {
            try {
                // Convert DTO to Entity
                Vehicle vehicle = vehicleConverter.convertToEntity(position);

                // Check if vehicle already exists
                boolean exists = vehicleRepository.exists(vehicle.getVehicleId());

                // Save to database
                vehicleRepository.save(vehicle);

                if (exists) {
                    updated++;
                } else {
                    saved++;
                }

            } catch (Exception e) {
                errors++;
                logger.error("Error saving vehicle " + position.getVehicleId() + ": " + e.getMessage());
            }
        }

        currentVehicleCount = saved + updated;

        logger.info("Database update complete: " + saved + " new, " + updated + " updated, " +
                   errors + " errors");
    }

    /**
     * Cleanup stale vehicle data (older than threshold)
     */
    private void cleanupStaleData() {
        try {
            logger.debug("Cleaning up stale vehicle data...");

            long beforeCount = vehicleRepository.count();
            vehicleRepository.cleanupStaleVehicles(STALE_DATA_THRESHOLD_MINUTES);
            long afterCount = vehicleRepository.count();

            long removed = beforeCount - afterCount;
            if (removed > 0) {
                logger.info("Removed " + removed + " stale vehicles (older than " +
                           STALE_DATA_THRESHOLD_MINUTES + " minutes)");
            }

            // Cleanup resolved incidents older than 1 day
            incidentRepository.cleanupOldIncidents(1);

        } catch (Exception e) {
            logger.warn("Error during cleanup: " + e.getMessage());
            // Non-critical, continue
        }
    }

    /**
     * Handle sync failure with retry logic
     */
    private void handleSyncFailure(Exception e) {
        logger.error("Sync cycle failed: " + e.getMessage(), e);

        // Try to use cached data
        try {
            logger.info("Attempting fallback to cached data...");

            List<VehiclePosition> cachedVehicles = gtfsRepository.loadVehiclePositions();
            if (!cachedVehicles.isEmpty()) {
                saveVehiclesToDatabase(cachedVehicles);
                isUsingCache.set(true);
                logger.info("Fallback successful: Using cached data (" + cachedVehicles.size() + " vehicles)");
            } else {
                logger.warn("No cached data available for fallback");
            }

        } catch (Exception fallbackError) {
            logger.error("Fallback also failed: " + fallbackError.getMessage());
        }
    }

    /**
     * Get sync service metrics
     */
    public SyncMetrics getMetrics() {
        return new SyncMetrics(
            isRunning.get(),
            totalSyncCycles.get(),
            successfulSyncs.get(),
            failedSyncs.get(),
            lastSuccessfulSync,
            lastAttemptedSync,
            currentVehicleCount,
            currentIncidentCount,
            isUsingCache.get()
        );
    }

    /**
     * Check if sync service is healthy
     */
    public boolean isHealthy() {
        if (!isRunning.get()) {
            return false;
        }

        if (lastSuccessfulSync == null) {
            return false;
        }

        // Check if last successful sync was within acceptable timeframe (5 minutes)
        long minutesSinceLastSync = Duration.between(lastSuccessfulSync, LocalDateTime.now()).toMinutes();
        return minutesSinceLastSync < 5;
    }

    /**
     * Get health status string
     */
    public String getHealthStatus() {
        if (!isRunning.get()) {
            return "STOPPED";
        }

        if (lastSuccessfulSync == null) {
            return "STARTING";
        }

        long minutesSinceLastSync = Duration.between(lastSuccessfulSync, LocalDateTime.now()).toMinutes();

        if (minutesSinceLastSync < 2) {
            return "HEALTHY";
        } else if (minutesSinceLastSync < 5) {
            return "WARNING";
        } else {
            return "UNHEALTHY";
        }
    }

    // === Inner Classes ===

    /**
     * Sync metrics data class
     */
    public static class SyncMetrics {
        private final boolean running;
        private final long totalCycles;
        private final long successfulCycles;
        private final long failedCycles;
        private final LocalDateTime lastSuccess;
        private final LocalDateTime lastAttempt;
        private final int vehicleCount;
        private final int incidentCount;
        private final boolean usingCache;

        public SyncMetrics(boolean running, long totalCycles, long successfulCycles,
                          long failedCycles, LocalDateTime lastSuccess, LocalDateTime lastAttempt,
                          int vehicleCount, int incidentCount, boolean usingCache) {
            this.running = running;
            this.totalCycles = totalCycles;
            this.successfulCycles = successfulCycles;
            this.failedCycles = failedCycles;
            this.lastSuccess = lastSuccess;
            this.lastAttempt = lastAttempt;
            this.vehicleCount = vehicleCount;
            this.incidentCount = incidentCount;
            this.usingCache = usingCache;
        }

        // Getters
        public boolean isRunning() { return running; }
        public long getTotalCycles() { return totalCycles; }
        public long getSuccessfulCycles() { return successfulCycles; }
        public long getFailedCycles() { return failedCycles; }
        public LocalDateTime getLastSuccess() { return lastSuccess; }
        public LocalDateTime getLastAttempt() { return lastAttempt; }
        public int getVehicleCount() { return vehicleCount; }
        public int getIncidentCount() { return incidentCount; }
        public boolean isUsingCache() { return usingCache; }

        public double getSuccessRate() {
            return totalCycles > 0 ? (double) successfulCycles / totalCycles * 100 : 0;
        }

        @Override
        public String toString() {
            return String.format(
                "SyncMetrics{running=%s, cycles=%d, success=%d, failed=%d, vehicles=%d, incidents=%d, cache=%s, successRate=%.1f%%}",
                running, totalCycles, successfulCycles, failedCycles, vehicleCount, incidentCount,
                usingCache, getSuccessRate()
            );
        }
    }
}
