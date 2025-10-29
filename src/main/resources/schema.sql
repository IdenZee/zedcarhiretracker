CREATE DATABASE IF NOT EXISTS gps_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE gps_data;

CREATE TABLE IF NOT EXISTS tracker_data (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            imei VARCHAR(32) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    speed_kph DOUBLE DEFAULT 0,
    course SMALLINT DEFAULT 0,
    acc TINYINT DEFAULT NULL,
    battery_mv INT DEFAULT NULL,
    mileage_m INT DEFAULT NULL,
    gps_time DATETIME NOT NULL,
    raw_hex TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_imei_time (imei, gps_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
