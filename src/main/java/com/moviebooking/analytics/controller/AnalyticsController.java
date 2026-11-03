package com.moviebooking.analytics.controller;

import com.moviebooking.analytics.dto.AnalyticsDto.AnalyticsResponse;
import com.moviebooking.analytics.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Analytics for the admin dashboard. */
@RestController
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/admin/analytics")
    public AnalyticsResponse analytics(@RequestParam(defaultValue = "7") int days) {
        return analytics.build(days);
    }
}
