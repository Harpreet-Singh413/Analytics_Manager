package com.analytics.manager.analytics.controller;

import com.analytics.manager.analytics.dto.*;
import com.analytics.manager.analytics.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary() {
        AnalyticsSummaryDto summary = analyticsService.getSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/events-by-type")
    public ResponseEntity<List<EventTypeCountDto>> getEventsByType(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        List<EventTypeCountDto> list = analyticsService.getEventsByType(from, to);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/events-over-time")
    public ResponseEntity<List<EventsOverTimeDto>> getEventsOverTime(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "minute") String interval
    ) {
        List<EventsOverTimeDto> list = analyticsService.getEventsOverTime(from, to, interval);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/clients")
    public ResponseEntity<List<ClientStatDto>> getTopClients(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "count") String sort
    ) {
        List<ClientStatDto> list = analyticsService.getTopClients(limit, sort);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/top-pages")
    public ResponseEntity<List<PageStatDto>> getTopPages(
            @RequestParam(defaultValue = "5") int limit
    ) {
        List<PageStatDto> list = analyticsService.getTopPages(limit);
        return ResponseEntity.ok(list);
    }
}
