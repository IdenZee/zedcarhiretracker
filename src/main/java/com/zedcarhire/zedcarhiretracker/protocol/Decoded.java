package com.zedcarhire.zedcarhiretracker.protocol;

import java.time.LocalDateTime;

public class Decoded {

    public String protocol;   // e.g. "GT06"
    public String imei;

    public Double latitude;
    public Double longitude;
    public Double speedKph;
    public Integer course;

    public Integer acc;
    public Integer batteryMv;
    public Integer mileageM;

    public LocalDateTime gpsTime;
    public String rawHex;

}
