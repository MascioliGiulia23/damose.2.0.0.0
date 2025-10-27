package com.rometransit.service.network;

import com.rometransit.model.enums.ConnectionStatus;
import com.rometransit.util.exception.NetworkException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkManager {
    private static NetworkManager instance;
    
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ConnectionStatusMonitor statusMonitor;
    
    private ConnectionStatus currentStatus;
    private LocalDateTime lastStatusCheck;
    private boolean autoMonitoring;

    private NetworkManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.scheduler = Executors.newScheduledThreadPool(2);
        this.statusMonitor = new ConnectionStatusMonitor();
        this.currentStatus = ConnectionStatus.OFFLINE;
        this.autoMonitoring = false;
    }

    public static synchronized NetworkManager getInstance() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    // Service lifecycle methods
    public void initialize() {
        System.out.println("NetworkManager initializing...");
        // Initialize connection monitoring
        startMonitoring();
    }

    public void startMonitoring() {
        if (autoMonitoring) {
            return;
        }

        autoMonitoring = true;

        // Perform immediate initial check
        checkConnectionStatus();

        // Check connection status every 30 seconds
        scheduler.scheduleAtFixedRate(this::checkConnectionStatus, 30, 30, TimeUnit.SECONDS);

        System.out.println("Network monitoring started with initial status: " + currentStatus);
    }

    public void stopMonitoring() {
        autoMonitoring = false;
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        System.out.println("Network monitoring stopped");
    }

    public boolean isOnline() {
        return currentStatus == ConnectionStatus.ONLINE;
    }

    /**
     * Check if online with optional real-time verification
     * @param forceCheck if true, performs a real-time connection test
     * @return true if connection is available
     */
    public boolean isOnline(boolean forceCheck) {
        if (forceCheck) {
            checkConnectionStatus();
        }
        return currentStatus == ConnectionStatus.ONLINE || currentStatus == ConnectionStatus.LIMITED;
    }

    public ConnectionStatus getConnectionStatus() {
        return currentStatus;
    }

    /**
     * Force an immediate connection status check
     */
    public ConnectionStatus refreshConnectionStatus() {
        checkConnectionStatus();
        return currentStatus;
    }

    public LocalDateTime getLastStatusCheck() {
        return lastStatusCheck;
    }

    public boolean testConnection() {
        return testConnection("https://www.google.com", 5000);
    }

    public boolean testConnection(String url, int timeoutMs) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80);

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean testHttpConnection(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 400;
            
        } catch (Exception e) {
            return false;
        }
    }

    public CompletableFuture<Boolean> testConnectionAsync(String url) {
        return CompletableFuture.supplyAsync(() -> testConnection(url, 5000));
    }

    public CompletableFuture<String> fetchDataAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchData(url);
            } catch (NetworkException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public String fetchData(String url) throws NetworkException {
        return fetchData(url, Duration.ofSeconds(30));
    }

    public String fetchData(String url, Duration timeout) throws NetworkException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", "Damose/2.0 (Rome Transit App)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                throw new NetworkException("HTTP " + response.statusCode() + " from " + url);
            }

        } catch (IOException | InterruptedException e) {
            throw new NetworkException("Failed to fetch data from " + url, e);
        }
    }

    public byte[] fetchBinaryData(String url) throws NetworkException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Damose/2.0 (Rome Transit App)")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                throw new NetworkException("HTTP " + response.statusCode() + " from " + url);
            }

        } catch (IOException | InterruptedException e) {
            throw new NetworkException("Failed to fetch binary data from " + url, e);
        }
    }

    public boolean downloadFile(String url, String localPath) {
        try {
            byte[] data = fetchBinaryData(url);
            java.nio.file.Files.write(java.nio.file.Paths.get(localPath), data);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to download file: " + e.getMessage());
            return false;
        }
    }

    private void checkConnectionStatus() {
        try {
            ConnectionStatus newStatus = determineConnectionStatus();
            
            if (newStatus != currentStatus) {
                ConnectionStatus oldStatus = currentStatus;
                currentStatus = newStatus;
                statusMonitor.onStatusChange(oldStatus, newStatus);
            }
            
            lastStatusCheck = LocalDateTime.now();
            
        } catch (Exception e) {
            System.err.println("Error checking connection status: " + e.getMessage());
            currentStatus = ConnectionStatus.ERROR;
        }
    }

    private ConnectionStatus determineConnectionStatus() {
        // Test multiple endpoints for better reliability
        String[] testUrls = {
            "https://www.google.com",
            "https://romamobilita.it",
            "https://www.atac.roma.it"
        };

        int successCount = 0;
        for (String url : testUrls) {
            if (testConnection(url, 3000)) {
                successCount++;
            }
        }

        if (successCount == 0) {
            return ConnectionStatus.OFFLINE;
        } else if (successCount == testUrls.length) {
            return ConnectionStatus.ONLINE;
        } else {
            return ConnectionStatus.LIMITED;
        }
    }

    public NetworkStats getNetworkStats() {
        NetworkStats stats = new NetworkStats();
        stats.currentStatus = currentStatus;
        stats.lastCheck = lastStatusCheck;
        stats.autoMonitoring = autoMonitoring;
        stats.httpClientStats = getHttpClientStats();
        return stats;
    }

    private String getHttpClientStats() {
        // Basic HTTP client information
        return "HttpClient - Java 11+ built-in client with 30s timeout";
    }

    public void addConnectionListener(ConnectionStatusListener listener) {
        statusMonitor.addListener(listener);
    }

    public void removeConnectionListener(ConnectionStatusListener listener) {
        statusMonitor.removeListener(listener);
    }

    public boolean canReachRomaMobilita() {
        return testConnection("https://romamobilita.it", 5000);
    }

    public boolean canReachGTFSEndpoint(String endpoint) {
        String baseUrl = "https://romamobilita.it/sites/default/files/gtfs/";
        return testHttpConnection(baseUrl + endpoint);
    }

    public void shutdown() {
        stopMonitoring();
        // HttpClient doesn't need explicit shutdown
        System.out.println("NetworkManager shutdown complete");
    }

    // Interface for connection status listeners
    public interface ConnectionStatusListener {
        void onStatusChanged(ConnectionStatus oldStatus, ConnectionStatus newStatus);
    }

    // Network statistics class
    public static class NetworkStats {
        public ConnectionStatus currentStatus;
        public LocalDateTime lastCheck;
        public boolean autoMonitoring;
        public String httpClientStats;

        @Override
        public String toString() {
            return "NetworkStats{" +
                    "currentStatus=" + currentStatus +
                    ", lastCheck=" + lastCheck +
                    ", autoMonitoring=" + autoMonitoring +
                    ", httpClient='" + httpClientStats + '\'' +
                    '}';
        }
    }
}