package com.analytics.manager.ingestion.validation;

import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EventValidationService {

    private static final Pattern UUID_REGEX = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_METADATA_KEYS = 20;
    private static final int MAX_METADATA_BYTES = 2048; // 2 KB

    private final Map<EventType, EventTypeValidator> validatorMap = new EnumMap<>(EventType.class);
    private final ObjectMapper objectMapper;

    public EventValidationService(List<EventTypeValidator> validators, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        for (EventTypeValidator validator : validators) {
            validatorMap.put(validator.supportedType(), validator);
        }
    }

    /**
     * Validates structural / semantic constraints.
     * Throws IllegalArgumentException for 400 Bad Request issues.
     * Throws EventTypeValidationException for 422 Unprocessable Entity issues.
     */
    public EventType validate(EventRequestDto dto) {
        // 1. Validate eventType syntax
        if (!EventType.isValid(dto.getEventType())) {
            throw new IllegalArgumentException("Unknown or invalid eventType: " + dto.getEventType());
        }
        EventType eventType = EventType.valueOf(dto.getEventType().trim().toUpperCase());

        // 2. Validate client-supplied eventId (must be valid UUID format)
        if (dto.getEventId() != null && !dto.getEventId().trim().isEmpty()) {
            if (!UUID_REGEX.matcher(dto.getEventId().trim()).matches()) {
                throw new IllegalArgumentException("eventId must be a valid UUID v4 format");
            }
        }

        // 3. Validate occurredAt timestamp sanity (if provided)
        if (dto.getOccurredAt() != null) {
            Instant now = Instant.now();
            if (dto.getOccurredAt().isAfter(now.plus(Duration.ofMinutes(5)))) {
                throw new IllegalArgumentException("occurredAt cannot be more than 5 minutes in the future");
            }
            if (dto.getOccurredAt().isBefore(now.minus(Duration.ofDays(30)))) {
                throw new IllegalArgumentException("occurredAt cannot be older than 30 days");
            }
        }

        // 4. Validate metadata (max 20 keys, max 2 KB serialized, scalar values)
        if (dto.getMetadata() != null) {
            if (dto.getMetadata().size() > MAX_METADATA_KEYS) {
                throw new IllegalArgumentException("metadata cannot contain more than " + MAX_METADATA_KEYS + " keys");
            }
            for (Map.Entry<String, Object> entry : dto.getMetadata().entrySet()) {
                Object val = entry.getValue();
                if (val != null && !(val instanceof String || val instanceof Number || val instanceof Boolean)) {
                    throw new IllegalArgumentException("metadata values must be string, number, or boolean");
                }
            }
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(dto.getMetadata());
                if (bytes.length > MAX_METADATA_BYTES) {
                    throw new IllegalArgumentException("metadata serialized size exceeds " + MAX_METADATA_BYTES + " bytes limit");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to serialize metadata: " + e.getMessage());
            }
        }

        // 5. Per-type cross-field validation rules (422 Unprocessable Entity)
        EventTypeValidator validator = validatorMap.get(eventType);
        if (validator != null) {
            List<String> typeErrors = validator.validate(dto);
            if (!typeErrors.isEmpty()) {
                throw new EventTypeValidationException(typeErrors);
            }
        }

        return eventType;
    }
}
