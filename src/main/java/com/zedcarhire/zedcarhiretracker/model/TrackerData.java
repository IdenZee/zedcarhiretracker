package com.zedcarhire.zedcarhiretracker.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tracker_data")
@Getter @Setter
public class TrackerData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String imei;

    private double latitude;
    private double longitude;

    @Column(name = "speed_kph")
    private double speedKph;

    private Integer course;
    private Integer acc;
    @Column(name = "battery_mv")
    private Integer batteryMv;
    @Column(name = "mileage_m")
    private Integer mileageM;

    @Column(name = "gps_time")
    private LocalDateTime gpsTime;

    @Lob
    @Column(name = "raw_hex")
    private String rawHex;
}
