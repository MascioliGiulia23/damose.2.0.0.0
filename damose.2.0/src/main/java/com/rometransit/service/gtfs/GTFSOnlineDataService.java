package com.rometransit.service.gtfs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rometransit.service.network.NetworkManager;
import com.rometransit.util.config.AppConfig;
import com.rometransit.util.exception.DataException;
import com.rometransit.util.exception.NetworkException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for downloading and managing GTFS data from online sources
 * Handles both static GTFS ZIP files and real-time data feeds
 */
public class GTFSOnlineDataService {

    private final NetworkManager networkManager;
    private final AppConfig config;
    private final Path cacheDirectory;
    private final Path metadataFile;

    // Download state tracking
    private boolean isDownloading = false;
    private LocalDateTime lastDownloadTime;
    private String lastDownloadedFileHash;
    private long lastDownloadedFileSize;
    private String lastETag;
    private String lastModified;

    // Online data URLs
    private final String gtfsStaticUrl;
    private final String vehiclePositionsUrl;
    private final String tripUpdatesUrl;
    private final String serviceAlertsUrl;

    public GTFSOnlineDataService() {
        this.networkManager = NetworkManager.getInstance();
        this.config = AppConfig.getInstance();

        // Initialize cache directory
        String userHome = System.getProperty("user.home");
        this.cacheDirectory = Paths.get(userHome, ".damose", "gtfs_downloads");
        this.metadataFile = cacheDirectory.resolve("download_metadata.json");

        try {
            Files.createDirectories(cacheDirectory);
            System.out.println("📁 GTFS download cache directory: " + cacheDirectory.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("⚠️ Could not create GTFS download cache directory: " + e.getMessage());
        }

        // Configure URLs from application.properties
        this.gtfsStaticUrl = config.getGtfsStaticDownloadUrl();
        this.vehiclePositionsUrl = config.getGtfsVehiclePositionsUrl();
        this.tripUpdatesUrl = config.getGtfsTripUpdatesUrl();
        this.serviceAlertsUrl = config.getGtfsServiceAlertsUrl();

        System.out.println("🌐 GTFSOnlineDataService initialized");
        System.out.println("   📦 Static GTFS URL: " + gtfsStaticUrl);
        System.out.println("   🚗 Vehicle positions URL: " + vehiclePositionsUrl);
        System.out.println("   🕐 Trip updates URL: " + tripUpdatesUrl);
        System.out.println("   ⚠️  Service alerts URL: " + serviceAlertsUrl);

        // Load metadata from previous downloads
        loadDownloadMetadata();
    }

    /**
     * Download static GTFS ZIP file from Roma Mobilità
     * @return Path to the downloaded file
     */
    public Path downloadGTFSZip() throws DataException {
        if (isDownloading) {
            throw new DataException("Download already in progress");
        }

        try {
            isDownloading = true;
            System.out.println("📥 Starting GTFS static data download...");
            System.out.println("   🌐 URL: " + gtfsStaticUrl);

            // Check internet connection first (with real-time check as fallback)
            if (!networkManager.isOnline() && !networkManager.testConnection()) {
                throw new DataException("No internet connection available");
            }

            // Generate timestamp-based filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "gtfs_static_" + timestamp + ".zip";
            Path targetPath = cacheDirectory.resolve(fileName);

            // Download the file
            System.out.println("   💾 Downloading to: " + targetPath);

            try {
                boolean success = networkManager.downloadFile(gtfsStaticUrl, targetPath.toString());

                if (!success) {
                    throw new DataException("Download failed - HTTP request unsuccessful. URL: " + gtfsStaticUrl);
                }
            } catch (Exception e) {
                throw new DataException("Network error during download: " + e.getMessage() + ". Check URL: " + gtfsStaticUrl, e);
            }

            // Verify downloaded file
            if (!Files.exists(targetPath)) {
                throw new DataException("Downloaded file does not exist at: " + targetPath);
            }

            if (Files.size(targetPath) == 0) {
                throw new DataException("Downloaded file is empty. The URL may be incorrect or the server returned an error. URL: " + gtfsStaticUrl);
            }

            if (Files.size(targetPath) < 1024) {
                // File is suspiciously small, might be an error page
                String content = new String(Files.readAllBytes(targetPath));
                System.err.println("⚠️ Downloaded file is very small (" + Files.size(targetPath) + " bytes). Content: " + content.substring(0, Math.min(200, content.length())));
                throw new DataException("Downloaded file is too small (" + Files.size(targetPath) + " bytes). This might be an error page. URL: " + gtfsStaticUrl);
            }

            long fileSize = Files.size(targetPath);
            System.out.println("   ✅ Download completed: " + formatBytes(fileSize));

            // Calculate file hash for change detection
            String fileHash = calculateFileHash(targetPath);

            // Update metadata
            lastDownloadTime = LocalDateTime.now();
            lastDownloadedFileHash = fileHash;
            lastDownloadedFileSize = fileSize;
            saveDownloadMetadata();

            System.out.println("   🔐 File hash: " + fileHash);
            System.out.println("   ⏰ Downloaded at: " + lastDownloadTime);

            // Create symlink to latest version
            createLatestSymlink(targetPath);

            return targetPath;

        } catch (IOException e) {
            throw new DataException("I/O error during GTFS download", e);
        } catch (Exception e) {
            throw new DataException("Error during GTFS download", e);
        } finally {
            isDownloading = false;
        }
    }

    /**
     * Download GTFS ZIP asynchronously
     */
    public CompletableFuture<Path> downloadGTFSZipAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadGTFSZip();
            } catch (DataException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Check if there's a newer version of GTFS data available online
     */
    public boolean isUpdateAvailable() {
        try {
            System.out.println("🔍 Checking for GTFS updates...");

            if (!networkManager.isOnline()) {
                System.out.println("   ⚠️ No internet connection");
                return false;
            }

            // Check using ETag and Last-Modified headers if available
            if (lastETag != null || lastModified != null) {
                try {
                    java.net.URL url = new java.net.URL(gtfsStaticUrl);
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    // Check ETag
                    String currentETag = connection.getHeaderField("ETag");
                    if (currentETag != null && lastETag != null) {
                        boolean etagChanged = !currentETag.equals(lastETag);
                        System.out.println("   🏷️ ETag check: " + (etagChanged ? "Changed" : "Unchanged"));
                        if (!etagChanged) {
                            System.out.println("   ℹ️ Data is up to date (ETag match)");
                            return false;
                        }
                        return true;
                    }

                    // Check Last-Modified
                    String currentLastModified = connection.getHeaderField("Last-Modified");
                    if (currentLastModified != null && lastModified != null) {
                        boolean modifiedChanged = !currentLastModified.equals(lastModified);
                        System.out.println("   📅 Last-Modified check: " + (modifiedChanged ? "Changed" : "Unchanged"));
                        if (!modifiedChanged) {
                            System.out.println("   ℹ️ Data is up to date (Last-Modified match)");
                            return false;
                        }
                        return true;
                    }

                    connection.disconnect();
                } catch (Exception e) {
                    System.err.println("   ⚠️ Could not check HTTP headers: " + e.getMessage());
                    // Fall through to time-based check
                }
            }

            // Fallback: check based on time since last download
            if (lastDownloadTime == null) {
                System.out.println("   ℹ️ No previous download found");
                return true;
            }

            LocalDateTime now = LocalDateTime.now();
            long daysSinceLastDownload = java.time.Duration.between(lastDownloadTime, now).toDays();

            System.out.println("   📅 Days since last download: " + daysSinceLastDownload);

            // Roma Mobilità typically updates GTFS data weekly
            boolean updateAvailable = daysSinceLastDownload >= 7;

            if (updateAvailable) {
                System.out.println("   ✅ Update available (data older than 7 days)");
            } else {
                System.out.println("   ℹ️ Data is up to date");
            }

            return updateAvailable;

        } catch (Exception e) {
            System.err.println("❌ Error checking for updates: " + e.getMessage());
            return false;
        }
    }

    /**
     * Download real-time vehicle positions data (Protocol Buffer format)
     */
    public byte[] downloadVehiclePositions() throws DataException {
        try {
            System.out.println("🚗 Downloading vehicle positions (protobuf)...");

            // Perform real-time connection check instead of relying on cached status
            if (!networkManager.isOnline() && !networkManager.testConnection()) {
                System.out.println("   ⚠️ Connection check failed, attempting download anyway...");
                // Don't throw immediately, try to download first
            }

            byte[] data = networkManager.fetchBinaryData(vehiclePositionsUrl);

            if (data == null || data.length == 0) {
                System.out.println("   ⚠️ No vehicle positions data available (this is normal outside operating hours)");
                return new byte[0]; // Return empty array instead of throwing exception
            }

            System.out.println("   ✅ Downloaded " + data.length + " bytes of vehicle data");
            return data;

        } catch (NetworkException e) {
            throw new DataException("Failed to download vehicle positions", e);
        }
    }

    /**
     * Download real-time trip updates data (Protocol Buffer format)
     */
    public byte[] downloadTripUpdates() throws DataException {
        try {
            System.out.println("🕐 Downloading trip updates (protobuf)...");

            // Perform real-time connection check instead of relying on cached status
            if (!networkManager.isOnline() && !networkManager.testConnection()) {
                System.out.println("   ⚠️ Connection check failed, attempting download anyway...");
                // Don't throw immediately, try to download first
            }

            byte[] data = networkManager.fetchBinaryData(tripUpdatesUrl);

            if (data == null || data.length == 0) {
                System.out.println("   ⚠️ No trip updates data available (this is normal outside operating hours)");
                return new byte[0]; // Return empty array instead of throwing exception
            }

            System.out.println("   ✅ Downloaded " + data.length + " bytes of trip update data");
            return data;

        } catch (NetworkException e) {
            throw new DataException("Failed to download trip updates", e);
        }
    }

    /**
     * Download real-time service alerts data (Protocol Buffer format)
     */
    public byte[] downloadServiceAlerts() throws DataException {
        try {
            System.out.println("⚠️  Downloading service alerts (protobuf)...");

            // Perform real-time connection check instead of relying on cached status
            if (!networkManager.isOnline() && !networkManager.testConnection()) {
                System.out.println("   ⚠️ Connection check failed, attempting download anyway...");
                // Don't throw immediately, try to download first
            }

            byte[] data = networkManager.fetchBinaryData(serviceAlertsUrl);

            if (data == null || data.length == 0) {
                System.out.println("   ⚠️ No service alerts data available");
                return new byte[0]; // Return empty array instead of throwing exception
            }

            System.out.println("   ✅ Downloaded " + data.length + " bytes of service alerts data");
            return data;

        } catch (NetworkException e) {
            throw new DataException("Failed to download service alerts", e);
        }
    }

    /**
     * Download all real-time feeds asynchronously
     */
    public CompletableFuture<Map<String, byte[]>> downloadRealtimeDataAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, byte[]> realtimeData = new HashMap<>();

            try {
                byte[] vehiclePositions = downloadVehiclePositions();
                realtimeData.put("vehicle_positions", vehiclePositions);
            } catch (DataException e) {
                System.err.println("⚠️ Failed to download vehicle positions: " + e.getMessage());
                realtimeData.put("vehicle_positions", null);
            }

            try {
                byte[] tripUpdates = downloadTripUpdates();
                realtimeData.put("trip_updates", tripUpdates);
            } catch (DataException e) {
                System.err.println("⚠️ Failed to download trip updates: " + e.getMessage());
                realtimeData.put("trip_updates", null);
            }

            try {
                byte[] serviceAlerts = downloadServiceAlerts();
                realtimeData.put("service_alerts", serviceAlerts);
            } catch (DataException e) {
                System.err.println("⚠️ Failed to download service alerts: " + e.getMessage());
                realtimeData.put("service_alerts", null);
            }

            return realtimeData;
        });
    }

    /**
     * Get the path to the latest downloaded GTFS file
     */
    public Path getLatestDownloadedFile() {
        Path latestLink = cacheDirectory.resolve("gtfs_static_latest.zip");
        if (Files.exists(latestLink)) {
            try {
                return Files.readSymbolicLink(latestLink);
            } catch (IOException e) {
                System.err.println("⚠️ Could not read latest symlink: " + e.getMessage());
            }
        }

        // Fallback: find newest file in cache directory
        try {
            return Files.list(cacheDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("gtfs_static_"))
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .max((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
        } catch (IOException e) {
            System.err.println("❌ Error finding latest file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Test connection to GTFS online endpoints
     */
    public boolean testConnection() {
        System.out.println("🔌 Testing GTFS online endpoints...");

        boolean staticOk = networkManager.testHttpConnection(gtfsStaticUrl);
        System.out.println("   📦 Static GTFS: " + (staticOk ? "✅ OK" : "❌ Failed"));

        boolean vehicleOk = networkManager.testHttpConnection(vehiclePositionsUrl);
        System.out.println("   🚗 Vehicle positions: " + (vehicleOk ? "✅ OK" : "❌ Failed"));

        boolean tripOk = networkManager.testHttpConnection(tripUpdatesUrl);
        System.out.println("   🕐 Trip updates: " + (tripOk ? "✅ OK" : "❌ Failed"));

        boolean alertsOk = networkManager.testHttpConnection(serviceAlertsUrl);
        System.out.println("   ⚠️  Service alerts: " + (alertsOk ? "✅ OK" : "❌ Failed"));

        return staticOk || vehicleOk || tripOk || alertsOk;
    }

    // === UTILITY METHODS ===

    private void createLatestSymlink(Path targetPath) {
        try {
            Path latestLink = cacheDirectory.resolve("gtfs_static_latest.zip");

            // Delete existing symlink if it exists
            Files.deleteIfExists(latestLink);

            // Create new symlink (on Windows, this might require admin privileges or create a copy instead)
            try {
                Files.createSymbolicLink(latestLink, targetPath);
                System.out.println("   🔗 Created symlink: gtfs_static_latest.zip");
            } catch (UnsupportedOperationException | IOException e) {
                // Fallback: create a copy instead of symlink (for Windows)
                Files.copy(targetPath, latestLink, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("   📋 Created copy: gtfs_static_latest.zip (symlinks not supported)");
            }
        } catch (IOException e) {
            System.err.println("⚠️ Could not create latest link: " + e.getMessage());
        }
    }

    private String calculateFileHash(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = md.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate file hash", e);
        }
    }

    private void loadDownloadMetadata() {
        if (!Files.exists(metadataFile)) {
            return;
        }

        try {
            String json = new String(Files.readAllBytes(metadataFile));
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            Map<String, Object> metadata = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            if (metadata.containsKey("lastDownloadTime")) {
                this.lastDownloadTime = LocalDateTime.parse((String) metadata.get("lastDownloadTime"));
            }
            if (metadata.containsKey("fileHash")) {
                this.lastDownloadedFileHash = (String) metadata.get("fileHash");
            }
            if (metadata.containsKey("fileSize")) {
                this.lastDownloadedFileSize = ((Number) metadata.get("fileSize")).longValue();
            }
            if (metadata.containsKey("etag")) {
                this.lastETag = (String) metadata.get("etag");
            }
            if (metadata.containsKey("lastModified")) {
                this.lastModified = (String) metadata.get("lastModified");
            }

            System.out.println("✅ Loaded download metadata from " + metadataFile);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load metadata: " + e.getMessage());
        }
    }

    private void saveDownloadMetadata() {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("lastDownloadTime", lastDownloadTime != null ? lastDownloadTime.toString() : null);
            metadata.put("fileHash", lastDownloadedFileHash);
            metadata.put("fileSize", lastDownloadedFileSize);
            metadata.put("etag", lastETag);
            metadata.put("lastModified", lastModified);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            String json = mapper.writeValueAsString(metadata);
            Files.write(metadataFile, json.getBytes());

            System.out.println("✅ Saved download metadata to " + metadataFile);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save metadata: " + e.getMessage());
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // === GETTERS ===

    public boolean isDownloading() {
        return isDownloading;
    }

    public LocalDateTime getLastDownloadTime() {
        return lastDownloadTime;
    }

    public String getLastDownloadedFileHash() {
        return lastDownloadedFileHash;
    }

    public long getLastDownloadedFileSize() {
        return lastDownloadedFileSize;
    }

    public Path getCacheDirectory() {
        return cacheDirectory;
    }

    public String getGtfsStaticUrl() {
        return gtfsStaticUrl;
    }

    public String getVehiclePositionsUrl() {
        return vehiclePositionsUrl;
    }

    public String getTripUpdatesUrl() {
        return tripUpdatesUrl;
    }

    public String getServiceAlertsUrl() {
        return serviceAlertsUrl;
    }

    /**
     * Get download statistics
     */
    public Map<String, Object> getDownloadStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("isDownloading", isDownloading);
        stats.put("lastDownloadTime", lastDownloadTime);
        stats.put("lastFileHash", lastDownloadedFileHash);
        stats.put("lastFileSize", lastDownloadedFileSize);
        stats.put("lastFileSizeFormatted", lastDownloadedFileSize > 0 ? formatBytes(lastDownloadedFileSize) : "N/A");
        stats.put("cacheDirectory", cacheDirectory.toString());
        stats.put("staticUrl", gtfsStaticUrl);
        stats.put("realtimeAvailable", networkManager.isOnline());
        return stats;
    }

    /**
     * Clear downloaded cache files
     */
    public void clearCache() {
        try {
            System.out.println("🗑️ Clearing GTFS download cache...");

            long deletedFiles = Files.list(cacheDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("gtfs_static_"))
                    .peek(p -> {
                        try {
                            Files.delete(p);
                            System.out.println("   🗑️ Deleted: " + p.getFileName());
                        } catch (IOException e) {
                            System.err.println("   ⚠️ Could not delete: " + p.getFileName());
                        }
                    })
                    .count();

            System.out.println("✅ Cache cleared: " + deletedFiles + " files deleted");

            // Reset metadata
            lastDownloadTime = null;
            lastDownloadedFileHash = null;
            lastDownloadedFileSize = 0;

        } catch (IOException e) {
            System.err.println("❌ Error clearing cache: " + e.getMessage());
        }
    }
}
