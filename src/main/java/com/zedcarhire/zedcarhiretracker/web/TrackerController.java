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

    // GET /api/tracker-data?imei=...&from=2025-10-28T00:00:00&to=2025-10-28T23:59:59
    @GetMapping("/tracker-data")
    public List<TrackerData> search(
            @RequestParam(required = false) String imei,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return service.search(imei, from, to);
    }

    // GET /api/last?imeis=3547...,3547...
    @GetMapping("/last")
    public List<TrackerData> last(@RequestParam String imeis) {
        List<String> list = Arrays.stream(imeis.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        return service.last(list);
    }

    // Optional: a simple POST for vendor HTTP forwarding (if ever used)
    // POST /api/push  (body: JSON { imei, lat, lng, speed, course, gps_time })
    @PostMapping("/push")
    public String push(@RequestBody TrackerData incoming) {
        if (incoming.getGpsTime() == null) incoming.setGpsTime(LocalDateTime.now());
        service.save(incoming);
        return "{\"ok\":true}";
    }
}
