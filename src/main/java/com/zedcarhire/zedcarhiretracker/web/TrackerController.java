package com.zedcarhire.zedcarhiretracker.web;

import com.zedcarhire.zedcarhiretracker.model.TrackerData;
import com.zedcarhire.zedcarhiretracker.service.TrackerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class TrackerController {

    private final TrackerService service;

    public TrackerController(TrackerService service) {
        this.service = service;
    }

    /**
     * Search / filter tracking data by IMEI + date range
     * Example:
     * GET /api/tracker-data?imei=356789123456789&from=2025-01-01T00:00:00&to=2025-01-01T23:59:59
     */
    @GetMapping("/tracker-data")
    public List<TrackerData> search(
            @RequestParam(name = "imei", required = false) String imei,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return service.search(imei, from, to);
    }

    /**
     * Get the latest positions for multiple trackers
     * Example:
     * GET /api/last?imeis=3547...,3547...
     */
    @GetMapping("/last")
    public List<TrackerData> last(@RequestParam("imeis") String imeis) {
        List<String> list = Arrays.stream(imeis.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        return service.last(list);
    }

    /**
     * Vendor Push Endpoint (Optional)
     * 3rd party devices can send POST JSON to store data directly
     */
    @PostMapping("/push")
    public String push(@RequestBody TrackerData incoming) {
        if (incoming.getGpsTime() == null) incoming.setGpsTime(LocalDateTime.now());
        service.save(incoming);
        return "{\"ok\":true}";
    }
}
