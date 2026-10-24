-- Demo locations (India + Southeast Asia + MENA) and one stored reading each,
-- so cached fallbacks and statistics work even before the first live call.
INSERT INTO locations (name, country, latitude, longitude, version, created_at, updated_at) VALUES
    ('Dehradun',  'India',                30.32, 78.03, 0, LOCALTIMESTAMP, LOCALTIMESTAMP),
    ('New Delhi', 'India',                28.61, 77.21, 0, LOCALTIMESTAMP, LOCALTIMESTAMP),
    ('Mumbai',    'India',                19.08, 72.88, 0, LOCALTIMESTAMP, LOCALTIMESTAMP),
    ('Singapore', 'Singapore',             1.29, 103.85, 0, LOCALTIMESTAMP, LOCALTIMESTAMP),
    ('Jakarta',   'Indonesia',            -6.21, 106.85, 0, LOCALTIMESTAMP, LOCALTIMESTAMP),
    ('Dubai',     'United Arab Emirates', 25.20, 55.27, 0, LOCALTIMESTAMP, LOCALTIMESTAMP),
    ('Riyadh',    'Saudi Arabia',         24.71, 46.68, 0, LOCALTIMESTAMP, LOCALTIMESTAMP);

INSERT INTO weather_snapshots (location_id, observed_at, temperature, apparent_temperature, humidity, wind_speed,
                               precipitation, weather_code, is_day, created_at, updated_at)
SELECT id, LOCALTIMESTAMP - INTERVAL '1' HOUR, 24.0, 24.5, 55, 8.0, 0.0, 2, TRUE, LOCALTIMESTAMP, LOCALTIMESTAMP
FROM locations;
