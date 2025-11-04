package com.zedcarhire.zedcarhiretracker.web;

import com.zedcarhire.zedcarhiretracker.model.TrackerData;
import com.zedcarhire.zedcarhiretracker.repo.TrackerDataRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final TrackerDataRepository repo;

    public TrackingController(TrackerDataRepository repo) {
        this.repo = repo;
    }

    // Live/latest position
    @GetMapping("/live")
    public TrackerData getLatest(@RequestParam("imei") String imei) {
        return repo.findTop1ByImeiOrderByGpsTimeDesc(imei)
                .orElseThrow(() -> new RuntimeException("No data for IMEI: " + imei));
    }

    // Route history for playback
    @GetMapping("/history")
    public List<TrackerData> getHistory(
            @RequestParam("imei") String imei,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        return repo.findByImeiAndGpsTimeBetweenOrderByGpsTimeAsc(imei, start, end);
    }

}
