package com.zedcarhire.zedcarhiretracker.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/health")
    public String ok() { return "OK"; }
}
