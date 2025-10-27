-- Damose 2.0 - SQLite Database Schema
-- Replaces JSON-based cache system with proper SQLite database

-- =====================================================
-- GTFS Static Data Tables
-- =====================================================

-- Agencies (transport companies)
CREATE TABLE IF NOT EXISTS agencies (
    agency_id TEXT PRIMARY KEY NOT NULL,
    agency_name TEXT NOT NULL,
    agency_url TEXT,
    agency_timezone TEXT NOT NULL,
    agency_lang TEXT,
    agency_phone TEXT,
    agency_fare_url TEXT,
    agency_email TEXT
);

-- Routes (bus/metro/tram lines)
CREATE TABLE IF NOT EXISTS routes (
    route_id TEXT PRIMARY KEY NOT NULL,
    agency_id TEXT,
    route_short_name TEXT,
    route_long_name TEXT,
    route_desc TEXT,
    route_type INTEGER NOT NULL,
    route_url TEXT,
    route_color TEXT,
    route_text_color TEXT,
    route_sort_order INTEGER DEFAULT 0,
    FOREIGN KEY (agency_id) REFERENCES agencies(agency_id)
);

CREATE INDEX IF NOT EXISTS idx_routes_agency ON routes(agency_id);
CREATE INDEX IF NOT EXISTS idx_routes_short_name ON routes(route_short_name);

-- Stops (bus stops, metro stations, etc.)
CREATE TABLE IF NOT EXISTS stops (
    stop_id TEXT PRIMARY KEY NOT NULL,
    stop_code TEXT,
    stop_name TEXT NOT NULL,
    stop_desc TEXT,
    stop_lat REAL NOT NULL,
    stop_lon REAL NOT NULL,
    zone_id TEXT,
    stop_url TEXT,
    location_type INTEGER DEFAULT 0,
    parent_station TEXT,
    stop_timezone TEXT,
    wheelchair_boarding INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_stops_name ON stops(stop_name);
CREATE INDEX IF NOT EXISTS idx_stops_code ON stops(stop_code);
CREATE INDEX IF NOT EXISTS idx_stops_location ON stops(stop_lat, stop_lon);

-- Trips (individual journeys)
CREATE TABLE IF NOT EXISTS trips (
    trip_id TEXT PRIMARY KEY NOT NULL,
    route_id TEXT NOT NULL,
    service_id TEXT,
    trip_headsign TEXT,
    trip_short_name TEXT,
    direction_id INTEGER DEFAULT 0,
    block_id TEXT,
    shape_id TEXT,
    wheelchair_accessible INTEGER DEFAULT 0,
    bikes_allowed INTEGER DEFAULT 0,
    FOREIGN KEY (route_id) REFERENCES routes(route_id)
);

CREATE INDEX IF NOT EXISTS idx_trips_route ON trips(route_id);
CREATE INDEX IF NOT EXISTS idx_trips_service ON trips(service_id);
CREATE INDEX IF NOT EXISTS idx_trips_shape ON trips(shape_id);

-- Stop Times (schedule for each stop in each trip)
CREATE TABLE IF NOT EXISTS stop_times (
    trip_id TEXT NOT NULL,
    arrival_time TEXT,
    departure_time TEXT,
    stop_id TEXT NOT NULL,
    stop_sequence INTEGER NOT NULL,
    stop_headsign TEXT,
    pickup_type INTEGER DEFAULT 0,
    drop_off_type INTEGER DEFAULT 0,
    shape_dist_traveled REAL DEFAULT 0.0,
    timepoint INTEGER DEFAULT 1,
    PRIMARY KEY (trip_id, stop_sequence),
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
    FOREIGN KEY (stop_id) REFERENCES stops(stop_id)
);

CREATE INDEX IF NOT EXISTS idx_stop_times_trip ON stop_times(trip_id);
CREATE INDEX IF NOT EXISTS idx_stop_times_stop ON stop_times(stop_id);
CREATE INDEX IF NOT EXISTS idx_stop_times_sequence ON stop_times(trip_id, stop_sequence);

-- Shapes (geographic path points for drawing routes on map)
CREATE TABLE IF NOT EXISTS shapes (
    shape_id TEXT NOT NULL,
    shape_pt_lat REAL NOT NULL,
    shape_pt_lon REAL NOT NULL,
    shape_pt_sequence INTEGER NOT NULL,
    shape_dist_traveled REAL DEFAULT 0.0,
    PRIMARY KEY (shape_id, shape_pt_sequence)
);

CREATE INDEX IF NOT EXISTS idx_shapes_id ON shapes(shape_id);
CREATE INDEX IF NOT EXISTS idx_shapes_sequence ON shapes(shape_id, shape_pt_sequence);

-- =====================================================
-- Realtime Data Tables
-- =====================================================

-- Vehicle Positions (realtime vehicle locations)
CREATE TABLE IF NOT EXISTS vehicle_positions (
    vehicle_id TEXT PRIMARY KEY NOT NULL,
    route_id TEXT,
    trip_id TEXT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    bearing REAL,
    speed REAL,
    status TEXT,
    timestamp INTEGER NOT NULL,
    congestion_level TEXT,
    occupancy_status TEXT,
    last_updated INTEGER NOT NULL,
    FOREIGN KEY (route_id) REFERENCES routes(route_id),
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id)
);

CREATE INDEX IF NOT EXISTS idx_vehicle_positions_route ON vehicle_positions(route_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_positions_trip ON vehicle_positions(trip_id);
CREATE INDEX IF NOT EXISTS idx_vehicle_positions_timestamp ON vehicle_positions(timestamp);

-- Trip Updates / Arrival Predictions (realtime predictions)
CREATE TABLE IF NOT EXISTS trip_updates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_id TEXT NOT NULL,
    route_id TEXT,
    stop_id TEXT NOT NULL,
    stop_sequence INTEGER,
    predicted_arrival INTEGER,
    predicted_departure INTEGER,
    delay_seconds INTEGER,
    schedule_relationship TEXT,
    last_updated INTEGER NOT NULL,
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
    FOREIGN KEY (route_id) REFERENCES routes(route_id),
    FOREIGN KEY (stop_id) REFERENCES stops(stop_id)
);

CREATE INDEX IF NOT EXISTS idx_trip_updates_trip ON trip_updates(trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_updates_stop ON trip_updates(stop_id);
CREATE INDEX IF NOT EXISTS idx_trip_updates_route ON trip_updates(route_id);
CREATE INDEX IF NOT EXISTS idx_trip_updates_timestamp ON trip_updates(last_updated);

-- =====================================================
-- Metadata Table
-- =====================================================

-- Database metadata (version, last update, etc.)
CREATE TABLE IF NOT EXISTS metadata (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Insert initial metadata
INSERT OR REPLACE INTO metadata (key, value, updated_at) VALUES
    ('version', '2.0.0', strftime('%s', 'now')),
    ('schema_version', '1', strftime('%s', 'now')),
    ('created_at', strftime('%s', 'now'), strftime('%s', 'now'));
