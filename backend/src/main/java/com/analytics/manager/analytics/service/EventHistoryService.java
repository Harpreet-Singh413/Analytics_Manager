package com.analytics.manager.analytics.service;

import com.analytics.manager.analytics.dto.EventDetailDto;
import com.analytics.manager.analytics.dto.FailedEventDto;
import com.analytics.manager.analytics.dto.PageResponseDto;
import com.analytics.manager.common.entity.EventEntity;
import com.analytics.manager.common.entity.FailedEventEntity;
import com.analytics.manager.common.exception.ResourceNotFoundException;
import com.analytics.manager.common.repository.EventRepository;
import com.analytics.manager.common.repository.FailedEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EventHistoryService {

    private static final Pattern UUID_REGEX = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final long MAX_QUERY_RANGE_DAYS = 7;

    private final EventRepository eventRepository;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    public EventHistoryService(EventRepository eventRepository,
                               FailedEventRepository failedEventRepository,
                               ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.failedEventRepository = failedEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponseDto<EventDetailDto> getEvents(String eventType,
                                                     String clientId,
                                                     Instant from,
                                                     Instant to,
                                                     Pageable pageable) {
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("'from' timestamp cannot be after 'to' timestamp");
            }
            if (Duration.between(from, to).toDays() > MAX_QUERY_RANGE_DAYS) {
                throw new IllegalArgumentException("Query range cannot exceed " + MAX_QUERY_RANGE_DAYS + " days");
            }
        }

        Specification<EventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null && !eventType.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
            }
            if (clientId != null && !clientId.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("clientId"), clientId.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<EventEntity> page = eventRepository.findAll(spec, pageable);
        return PageResponseDto.from(page.map(this::mapToProcessedDetailDto));
    }

    @Transactional(readOnly = true)
    public EventDetailDto getEventById(String eventId) {
        if (eventId == null || !UUID_REGEX.matcher(eventId.trim()).matches()) {
            throw new IllegalArgumentException("eventId must be a valid UUID format");
        }
        String cleanId = eventId.trim().toLowerCase();

        // 1. Check successfully processed events
        Optional<EventEntity> eventOpt = eventRepository.findByEventId(cleanId);
        if (eventOpt.isPresent()) {
            return mapToProcessedDetailDto(eventOpt.get());
        }

        // 2. Check failed DLQ events
        Optional<FailedEventEntity> failedOpt = failedEventRepository.findByEventId(cleanId);
        if (failedOpt.isPresent()) {
            return mapToFailedDetailDto(failedOpt.get());
        }

        // 3. Not found in either (Pending or Non-existent)
        throw new ResourceNotFoundException("Event with ID " + eventId + " not found (may still be pending or invalid)");
    }

    @Transactional(readOnly = true)
    public PageResponseDto<FailedEventDto> getFailedEvents(Pageable pageable) {
        Page<FailedEventEntity> page = failedEventRepository.findAllByOrderByFailedAtDesc(pageable);
        return PageResponseDto.from(page.map(fe -> FailedEventDto.builder()
                .id(fe.getId())
                .eventId(fe.getEventId())
                .payload(fe.getPayload())
                .errorClass(fe.getErrorClass())
                .errorMessage(fe.getErrorMessage())
                .originalTopic(fe.getOriginalTopic())
                .originalPartition(fe.getOriginalPartition())
                .originalOffset(fe.getOriginalOffset())
                .failedAt(fe.getFailedAt())
                .build()));
    }

    private EventDetailDto mapToProcessedDetailDto(EventEntity entity) {
        Map<String, Object> metadataMap = null;
        if (entity.getMetadata() != null && !entity.getMetadata().trim().isEmpty()) {
            try {
                metadataMap = objectMapper.readValue(entity.getMetadata(), new TypeReference<>() {});
            } catch (Exception e) {
                log.debug("Failed to deserialize metadata for eventId={}: {}", entity.getEventId(), e.getMessage());
            }
        }

        return EventDetailDto.builder()
                .eventId(entity.getEventId())
                .eventType(entity.getEventType())
                .clientId(entity.getClientId())
                .occurredAt(entity.getOccurredAt())
                .receivedAt(entity.getReceivedAt())
                .processedAt(entity.getProcessedAt())
                .source(entity.getSource())
                .schemaVersion(entity.getSchemaVersion())
                .page(entity.getPage())
                .productId(entity.getProductId())
                .metadata(metadataMap)
                .kafkaPartition(entity.getKafkaPartition())
                .kafkaOffset(entity.getKafkaOffset())
                .status("PROCESSED")
                .build();
    }

    private EventDetailDto mapToFailedDetailDto(FailedEventEntity failed) {
        return EventDetailDto.builder()
                .eventId(failed.getEventId())
                .source("unknown")
                .status("FAILED")
                .errorClass(failed.getErrorClass())
                .errorMessage(failed.getErrorMessage())
                .failedAt(failed.getFailedAt())
                .kafkaPartition(failed.getOriginalPartition())
                .kafkaOffset(failed.getOriginalOffset())
                .build();
    }
}
