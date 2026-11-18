package com.analytics.manager.analytics.controller;

import com.analytics.manager.analytics.dto.EventDetailDto;
import com.analytics.manager.analytics.dto.FailedEventDto;
import com.analytics.manager.analytics.dto.PageResponseDto;
import com.analytics.manager.analytics.service.EventHistoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/events")
public class EventHistoryController {

    private final EventHistoryService eventHistoryService;

    public EventHistoryController(EventHistoryService eventHistoryService) {
        this.eventHistoryService = eventHistoryService;
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<EventDetailDto>> getEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt,desc") String sort
    ) {
        if (size > 100) {
            throw new IllegalArgumentException("Page size cannot exceed 100");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be negative");
        }

        Sort sortOrder = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        PageResponseDto<EventDetailDto> response = eventHistoryService.getEvents(eventType, clientId, from, to, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventDetailDto> getEventById(@PathVariable String eventId) {
        EventDetailDto dto = eventHistoryService.getEventById(eventId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/failed")
    public ResponseEntity<PageResponseDto<FailedEventDto>> getFailedEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size > 100) {
            throw new IllegalArgumentException("Page size cannot exceed 100");
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), size);
        PageResponseDto<FailedEventDto> response = eventHistoryService.getFailedEvents(pageable);
        return ResponseEntity.ok(response);
    }

    private Sort parseSort(String sortStr) {
        if (sortStr == null || sortStr.trim().isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "occurredAt");
        }
        String[] parts = sortStr.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
