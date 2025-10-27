package com.rometransit.service.gtfs;

import com.rometransit.model.entity.*;
import com.rometransit.model.enums.TransportType;
import com.rometransit.service.network.NetworkManager;
import com.rometransit.util.config.AppConfig;
import com.rometransit.util.exception.DataException;
import com.rometransit.util.exception.NetworkException;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * High-performance GTFS parser that extracts data from ZIP files
 * and saves to SQLite database via GTFSRepository.
 */
public class GTFSParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final com.rometransit.data.repository.GTFSRepository repository;

    // Data collections
    private List<Agency> agencies = new ArrayList<>();
    private List<Stop> stops = new ArrayList<>();
    private List<Route> routes = new ArrayList<>();
    private List<Trip> trips = new ArrayList<>();
    private List<StopTime> stopTimes = new ArrayList<>();
    private List<GTFSCalendar> calendars = new ArrayList<>();
    private List<Shape> shapes = new ArrayList<>();

    // Statistics
    private int stopsCount = 0;
    private int routesCount = 0;
    private int tripsCount = 0;
    private int stopTimesCount = 0;
    private int shapesCount = 0;
    private int agenciesCount = 0;

    public GTFSParser(com.rometransit.data.repository.GTFSRepository repository) {
        this.repository = repository;
    }

    // Legacy constructor for backward compatibility
    @Deprecated
    public GTFSParser(Object cacheManager) {
        this.repository = com.rometransit.data.repository.GTFSRepository.getInstance();
        System.out.println("⚠️  Using deprecated GTFSParser constructor - migrated to SQLite");
    }

    /**
     * Parse GTFS ZIP file and populate JSON cache
     */
    public void parseGTFSZip(String zipFilePath) throws DataException {
        System.out.println("🚀 Starting GTFS parsing from: " + zipFilePath);

        // Create temporary directory for extraction
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("gtfs_extract_");
            System.out.println("📂 Created temp directory: " + tempDir);

            // Extract ZIP file
            extractZipFile(zipFilePath, tempDir);

            // Parse each GTFS file
            parseAgencies(tempDir.resolve("agency.txt"));
            parseStops(tempDir.resolve("stops.txt"));
            parseRoutes(tempDir.resolve("routes.txt"));
            parseTrips(tempDir.resolve("trips.txt"));
            parseStopTimes(tempDir.resolve("stop_times.txt"));
            parseCalendar(tempDir.resolve("calendar.txt"));
            parseShapes(tempDir.resolve("shapes.txt"));

            // Save all data to cache
            saveAllToCache(zipFilePath);

        } catch (IOException e) {
            throw new DataException("Failed to parse GTFS ZIP file", e);
        } finally {
            // Cleanup temporary directory
            if (tempDir != null) {
                cleanupTempDirectory(tempDir);
            }
        }

        System.out.println("✅ GTFS parsing completed successfully");
    }

    private void extractZipFile(String zipFilePath, Path targetDir) throws IOException {
        System.out.println("📦 Extracting ZIP file...");

        try (FileInputStream fis = new FileInputStream(zipFilePath);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                Path filePath = targetDir.resolve(entry.getName());

                // Create parent directories if needed
                Files.createDirectories(filePath.getParent());

                // Extract file
                try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, length);
                    }
                }

                System.out.println("  📄 Extracted: " + entry.getName() + " (" + Files.size(filePath) + " bytes)");
                zis.closeEntry();
            }
        }

        System.out.println("✅ ZIP extraction completed");
    }

    private void parseAgencies(Path filePath) throws DataException {
        if (!Files.exists(filePath)) {
            System.out.println("⚠️  Agency file not found, skipping");
            return;
        }

        System.out.println("🏢 Parsing agencies...");
        agencies.clear();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnMap = createColumnMap(headers);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);

                Agency agency = new Agency();
                agency.setAgencyId(getColumnValue(values, columnMap, "agency_id"));
                agency.setAgencyName(getColumnValue(values, columnMap, "agency_name"));
                agency.setAgencyUrl(getColumnValue(values, columnMap, "agency_url"));
                agency.setAgencyTimezone(getColumnValue(values, columnMap, "agency_timezone"));
                agency.setAgencyLang(getColumnValue(values, columnMap, "agency_lang"));
                agency.setAgencyPhone(getColumnValue(values, columnMap, "agency_phone"));
                agency.setAgencyFareUrl(getColumnValue(values, columnMap, "agency_fare_url"));
                agency.setAgencyEmail(getColumnValue(values, columnMap, "agency_email"));

                agencies.add(agency);
                count++;
            }

            System.out.println("✅ Parsed " + count + " agencies");

        } catch (IOException e) {
            throw new DataException("Failed to parse agencies", e);
        }
    }

    private void parseStops(Path filePath) throws DataException {
        if (!Files.exists(filePath)) {
            System.out.println("⚠️  Stops file not found, skipping");
            return;
        }

        System.out.println("🚏 Parsing stops...");
        stops.clear();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnMap = createColumnMap(headers);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);

                Stop stop = new Stop();
                stop.setStopId(getColumnValue(values, columnMap, "stop_id"));
                stop.setStopCode(getColumnValue(values, columnMap, "stop_code"));
                stop.setStopName(getColumnValue(values, columnMap, "stop_name"));
                stop.setStopDesc(getColumnValue(values, columnMap, "stop_desc"));
                stop.setStopLat(parseDouble(getColumnValue(values, columnMap, "stop_lat")));
                stop.setStopLon(parseDouble(getColumnValue(values, columnMap, "stop_lon")));
                stop.setZoneId(getColumnValue(values, columnMap, "zone_id"));
                stop.setStopUrl(getColumnValue(values, columnMap, "stop_url"));
                stop.setLocationType(parseInt(getColumnValue(values, columnMap, "location_type")));
                stop.setParentStation(getColumnValue(values, columnMap, "parent_station"));
                stop.setStopTimezone(getColumnValue(values, columnMap, "stop_timezone"));
                stop.setWheelchairBoarding(parseInt(getColumnValue(values, columnMap, "wheelchair_boarding")));

                stops.add(stop);
                count++;

                if (count % 5000 == 0) {
                    System.out.println("  📊 Processed " + count + " stops...");
                }
            }

            System.out.println("✅ Parsed " + count + " stops");

        } catch (IOException e) {
            throw new DataException("Failed to parse stops", e);
        }
    }

    private void parseRoutes(Path filePath) throws DataException {
        if (!Files.exists(filePath)) {
            System.out.println("⚠️  Routes file not found, skipping");
            return;
        }

        System.out.println("🚌 Parsing routes...");
        routes.clear();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnMap = createColumnMap(headers);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);

                Route route = new Route();
                route.setRouteId(getColumnValue(values, columnMap, "route_id"));
                route.setAgencyId(getColumnValue(values, columnMap, "agency_id"));
                route.setRouteShortName(getColumnValue(values, columnMap, "route_short_name"));
                route.setRouteLongName(getColumnValue(values, columnMap, "route_long_name"));
                route.setRouteDesc(getColumnValue(values, columnMap, "route_desc"));

                int routeTypeInt = parseInt(getColumnValue(values, columnMap, "route_type"));
                route.setRouteType(TransportType.fromGtfsType(routeTypeInt));

                route.setRouteUrl(getColumnValue(values, columnMap, "route_url"));
                route.setRouteColor(getColumnValue(values, columnMap, "route_color"));
                route.setRouteTextColor(getColumnValue(values, columnMap, "route_text_color"));
                route.setRouteSortOrder(parseInt(getColumnValue(values, columnMap, "route_sort_order")));

                routes.add(route);
                count++;
            }

            System.out.println("✅ Parsed " + count + " routes");

        } catch (IOException e) {
            throw new DataException("Failed to parse routes", e);
        }
    }

    private void parseTrips(Path filePath) throws DataException {
        if (!Files.exists(filePath)) {
            System.out.println("⚠️  Trips file not found, skipping");
            return;
        }

        System.out.println("🚗 Parsing trips...");
        trips.clear();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnMap = createColumnMap(headers);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);

                Trip trip = new Trip();
                trip.setRouteId(getColumnValue(values, columnMap, "route_id"));
                trip.setServiceId(getColumnValue(values, columnMap, "service_id"));
                trip.setTripId(getColumnValue(values, columnMap, "trip_id"));
                trip.setTripHeadsign(getColumnValue(values, columnMap, "trip_headsign"));
                trip.setTripShortName(getColumnValue(values, columnMap, "trip_short_name"));
                trip.setDirectionId(parseInt(getColumnValue(values, columnMap, "direction_id")));
                trip.setBlockId(getColumnValue(values, columnMap, "block_id"));
                trip.setShapeId(getColumnValue(values, columnMap, "shape_id"));
                trip.setWheelchairAccessible(parseInt(getColumnValue(values, columnMap, "wheelchair_accessible")));
                trip.setBikesAllowed(parseInt(getColumnValue(values, columnMap, "bikes_allowed")));

                trips.add(trip);
                count++;

                if (count % 10000 == 0) {
                    System.out.println("  📊 Processed " + count + " trips...");
                }
            }

            System.out.println("✅ Parsed " + count + " trips");

        } catch (IOException e) {
            throw new DataException("Failed to parse trips", e);
        }
    }

    private void parseCalendar(Path filePath) throws DataException {
        if (!Files.exists(filePath)) {
            System.out.println("⚠️  Calendar file not found, skipping");
            return;
        }

        System.out.println("📅 Parsing calendar...");
        calendars.clear();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnMap = createColumnMap(headers);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);

                GTFSCalendar calendar = new GTFSCalendar();
                calendar.setServiceId(getColumnValue(values, columnMap, "service_id"));
                calendar.setMonday(parseInt(getColumnValue(values, columnMap, "monday")) == 1);
                calendar.setTuesday(parseInt(getColumnValue(values, columnMap, "tuesday")) == 1);
                calendar.setWednesday(parseInt(getColumnValue(values, columnMap, "wednesday")) == 1);
                calendar.setThursday(parseInt(getColumnValue(values, columnMap, "thursday")) == 1);
                calendar.setFriday(parseInt(getColumnValue(values, columnMap, "friday")) == 1);
                calendar.setSaturday(parseInt(getColumnValue(values, columnMap, "saturday")) == 1);
                calendar.setSunday(parseInt(getColumnValue(values, columnMap, "sunday")) == 1);

                String startDateStr = getColumnValue(values, columnMap, "start_date");
                if (startDateStr != null && !startDateStr.isEmpty()) {
                    calendar.setStartDate(LocalDate.parse(startDateStr, DATE_FORMAT));
                }

                String endDateStr = getColumnValue(values, columnMap, "end_date");
                if (endDateStr != null && !endDateStr.isEmpty()) {
                    calendar.setEndDate(LocalDate.parse(endDateStr, DATE_FORMAT));
                }

                calendars.add(calendar);
                count++;
            }

            System.out.println("✅ Parsed " + count + " calendar entries");

        } catch (IOException e) {
            throw new DataException("Failed to parse calendar", e);
        }
    }

    private void parseShapes(Path filePath) throws DataException {
        if (!Files.exists(filePath)) {
            System.out.println("⚠️  Shapes file not found, skipping");
            return;
        }

        System.out.println("🗺️  Parsing shapes...");
        shapes.clear();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnMap = createColumnMap(headers);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);

                Shape shape = new Shape();
                shape.setShapeId(getColumnValue(values, columnMap, "shape_id"));
                shape.setShapePtLat(parseDouble(getColumnValue(values, columnMap, "shape_pt_lat")));
                shape.setShapePtLon(parseDouble(getColumnValue(values, columnMap, "shape_pt_lon")));
                shape.setShapePtSequence(parseInt(getColumnValue(values, columnMap, "shape_pt_sequence")));
                shape.setShapeDistTraveled(parseDouble(getColumnValue(values, columnMap, "shape_dist_traveled")));

                shapes.add(shape);
                count++;

                if (count % 25000 == 0) {
                    System.out.println("  📊 Processed " + count + " shape points...");
                }
            }

            System.out.println("✅ Parsed " + count + " shape points");

        } catch (IOException e) {
            throw new DataException("Failed to parse shapes", e);
        }
    }

    private void saveAllToCache(String originalFilePath) throws DataException {
        System.out.println("💾 Saving parsed data to SQLite database...");

        try {
            repository.saveAllGTFSData(agencies, routes, stops, trips, stopTimes, shapes);
            System.out.println("✅ All data saved to database successfully");
        } catch (Exception e) {
            System.err.println("❌ Failed to save data to database: " + e.getMessage());
            e.printStackTrace();
            throw new DataException("Failed to save data to database", e);
        }
    }

    private void parseStopTimes(Path filePath) throws DataException {
        if (!Files.exists(filePath)) {
            System.out.println("⚠️  Stop times file not found, skipping");
            return;
        }

        System.out.println("⏰ Parsing stop_times...");
        System.out.println("   WARNING: This file is usually VERY large (millions of rows)");
        System.out.println("   Parsing may take several minutes...");
        stopTimes.clear();

        long startTime = System.currentTimeMillis();
        int count = 0;
        int batchSize = 10000;

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnMap = createColumnMap(headers);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);

                StopTime stopTime = new StopTime();
                stopTime.setTripId(getColumnValue(values, columnMap, "trip_id"));
                stopTime.setArrivalTime(getColumnValue(values, columnMap, "arrival_time"));
                stopTime.setDepartureTime(getColumnValue(values, columnMap, "departure_time"));
                stopTime.setStopId(getColumnValue(values, columnMap, "stop_id"));
                stopTime.setStopSequence(parseInt(getColumnValue(values, columnMap, "stop_sequence")));
                stopTime.setStopHeadsign(getColumnValue(values, columnMap, "stop_headsign"));
                stopTime.setPickupType(parseInt(getColumnValue(values, columnMap, "pickup_type")));
                stopTime.setDropOffType(parseInt(getColumnValue(values, columnMap, "drop_off_type")));
                stopTime.setShapeDistTraveled(parseDouble(getColumnValue(values, columnMap, "shape_dist_traveled")));
                stopTime.setTimepoint(parseInt(getColumnValue(values, columnMap, "timepoint")));

                stopTimes.add(stopTime);
                count++;

                // Progress report every batch
                if (count % batchSize == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double rate = count * 1000.0 / elapsed;
                    System.out.printf("   Parsed %,d stop_times (%.0f/sec)%n", count, rate);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            double avgRate = count * 1000.0 / elapsed;
            System.out.printf("✅ Parsed %,d stop_times in %,dms (avg %.0f/sec)%n",
                            count, elapsed, avgRate);

        } catch (IOException e) {
            throw new DataException("Failed to parse stop_times", e);
        }
    }

    // === UTILITY METHODS ===

    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"' && !inQuotes) {
                inQuotes = true;
            } else if (c == '"' && inQuotes) {
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = false;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        fields.add(currentField.toString().trim());
        return fields.toArray(new String[0]);
    }

    private Map<String, Integer> createColumnMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].toLowerCase().trim(), i);
        }
        return map;
    }

    private String getColumnValue(String[] values, Map<String, Integer> columnMap, String columnName) {
        Integer index = columnMap.get(columnName.toLowerCase());
        if (index == null || index >= values.length) {
            return null;
        }
        String value = values[index];
        return value.isEmpty() ? null : value;
    }

    private double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void cleanupTempDirectory(Path tempDir) {
        try {
            System.out.println("🧹 Cleaning up temporary directory...");
            Files.walk(tempDir)
                .sorted((path1, path2) -> path2.compareTo(path1)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Warning: Could not delete " + path + ": " + e.getMessage());
                    }
                });
            System.out.println("✅ Cleanup completed");
        } catch (IOException e) {
            System.err.println("Warning: Error during cleanup: " + e.getMessage());
        }
    }
}