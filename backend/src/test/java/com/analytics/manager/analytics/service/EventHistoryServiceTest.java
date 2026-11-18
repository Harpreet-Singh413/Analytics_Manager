package com.analytics.manager.analytics.service;

import com.analytics.manager.analytics.dto.EventDetailDto;
import com.analytics.manager.analytics.dto.FailedEventDto;
import com.analytics.manager.analytics.dto.PageResponseDto;
import com.analytics.manager.common.entity.EventEntity;
import com.analytics.manager.common.entity.FailedEventEntity;
import com.analytics.manager.common.exception.ResourceNotFoundException;
import com.analytics.manager.common.repository.EventRepository;
import com.analytics.manager.common.repository.FailedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventHistoryServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private FailedEventRepository failedEventRepository;

    private EventHistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new EventHistoryService(eventRepository, failedEventRepository, new ObjectMapper());
    }

    @Test
    void testGetEventsReturnsPaginatedResults() {
        EventEntity entity = EventEntity.builder()
                .eventId("3f2b8c1e-5a0d-4e7b-9c1a-2d6f4b8e1a90")
                .eventType("PAGE_VIEW")
                .clientId("client-1")
                .occurredAt(Instant.now())
                .receivedAt(Instant.now())
                .processedAt(Instant.now())
                .source("simulator")
                .schemaVersion(1)
                .page("/home")
                .build();

        Page<EventEntity> page = new PageImpl<>(Collections.singletonList(entity));
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponseDto<EventDetailDto> result = historyService.getEvents("PAGE_VIEW", "client-1", null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("3f2b8c1e-5a0d-4e7b-9c1a-2d6f4b8e1a90", result.getContent().get(0).getEventId());
        assertEquals("PROCESSED", result.getContent().get(0).getStatus());
    }

    @Test
    void testGetEventsRejectsRangeGreaterThan7Days() {
        Instant from = Instant.now().minus(Duration.ofDays(10));
        Instant to = Instant.now();

        assertThrows(IllegalArgumentException.class, () ->
                historyService.getEvents(null, null, from, to, PageRequest.of(0, 10)));
    }

    @Test
    void testGetEventsRejectsFromAfterTo() {
        Instant from = Instant.now();
        Instant to = from.minus(Duration.ofHours(1));

        assertThrows(IllegalArgumentException.class, () ->
                historyService.getEvents(null, null, from, to, PageRequest.of(0, 10)));
    }

    @Test
    void testGetEventByIdProcessed() {
        String eventId = UUID.randomUUID().toString();
        EventEntity entity = EventEntity.builder()
                .eventId(eventId)
                .eventType("LOGIN")
                .clientId("user-100")
                .occurredAt(Instant.now())
                .receivedAt(Instant.now())
                .processedAt(Instant.now())
                .source("web")
                .schemaVersion(1)
                .build();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(entity));

        EventDetailDto dto = historyService.getEventById(eventId);

        assertNotNull(dto);
        assertEquals(eventId, dto.getEventId());
        assertEquals("PROCESSED", dto.getStatus());
    }

    @Test
    void testGetEventByIdFailedInDlq() {
        String eventId = UUID.randomUUID().toString();
        FailedEventEntity failed = FailedEventEntity.builder()
                .eventId(eventId)
                .errorClass("IllegalArgumentException")
                .errorMessage("Missing page")
                .failedAt(Instant.now())
                .originalTopic("analytics-events")
                .originalPartition(0)
                .originalOffset(45L)
                .build();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(failedEventRepository.findByEventId(eventId)).thenReturn(Optional.of(failed));

        EventDetailDto dto = historyService.getEventById(eventId);

        assertNotNull(dto);
        assertEquals(eventId, dto.getEventId());
        assertEquals("FAILED", dto.getStatus());
        assertEquals("IllegalArgumentException", dto.getErrorClass());
        assertEquals("Missing page", dto.getErrorMessage());
    }

    @Test
    void testGetEventByIdNotFoundThrowsResourceNotFoundException() {
        String eventId = UUID.randomUUID().toString();
        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(failedEventRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> historyService.getEventById(eventId));
    }

    @Test
    void testGetEventByIdInvalidUuidThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> historyService.getEventById("not-a-uuid"));
    }

    @Test
    void testGetFailedEventsReturnsPaginatedResults() {
        FailedEventEntity fe = FailedEventEntity.builder()
                .id(1L)
                .eventId("some-uuid")
                .payload("{...}")
                .errorClass("JsonProcessingException")
                .errorMessage("Malformed JSON")
                .originalTopic("analytics-events")
                .originalPartition(0)
                .originalOffset(10L)
                .failedAt(Instant.now())
                .build();

        when(failedEventRepository.findAllByOrderByFailedAtDesc(any())).thenReturn(new PageImpl<>(Collections.singletonList(fe)));

        PageResponseDto<FailedEventDto> result = historyService.getFailedEvents(PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("some-uuid", result.getContent().get(0).getEventId());
        assertEquals("Malformed JSON", result.getContent().get(0).getErrorMessage());
    }
}
