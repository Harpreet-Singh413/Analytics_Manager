package com.analytics.manager.ingestion.controller;

import com.analytics.manager.common.model.AnalyticsEvent;
import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.BatchEventRequestDto;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.analytics.manager.ingestion.exception.GlobalExceptionHandler;
import com.analytics.manager.ingestion.exception.KafkaPublishException;
import com.analytics.manager.ingestion.service.EventEnrichmentService;
import com.analytics.manager.ingestion.service.KafkaProducerService;
import com.analytics.manager.ingestion.validation.EventTypeValidationException;
import com.analytics.manager.ingestion.validation.EventValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventIngestionController.class)
@Import(GlobalExceptionHandler.class)
class EventIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventValidationService validationService;

    @MockitoBean
    private EventEnrichmentService enrichmentService;

    @MockitoBean
    private KafkaProducerService producerService;

    @Test
    void testIngestEventSuccessReturns202() throws Exception {
        EventRequestDto request = EventRequestDto.builder()
                .eventType("PAGE_VIEW")
                .clientId("client-1")
                .page("/home")
                .build();

        AnalyticsEvent enriched = AnalyticsEvent.builder()
                .eventId("3f2b8c1e-5a0d-4e7b-9c1a-2d6f4b8e1a90")
                .eventType("PAGE_VIEW")
                .clientId("client-1")
                .receivedAt(Instant.now())
                .build();

        when(validationService.validate(any())).thenReturn(EventType.PAGE_VIEW);
        when(enrichmentService.enrich(any(), any())).thenReturn(enriched);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/events/3f2b8c1e-5a0d-4e7b-9c1a-2d6f4b8e1a90"))
                .andExpect(jsonPath("$.eventId").value("3f2b8c1e-5a0d-4e7b-9c1a-2d6f4b8e1a90"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.receivedAt").exists());
    }

    @Test
    void testIngestEventMissingClientIdReturns400() throws Exception {
        EventRequestDto request = EventRequestDto.builder()
                .eventType("PAGE_VIEW")
                .clientId("") // Blank clientId violates validation
                .build();

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void testIngestEventSemanticTypeFailureReturns422() throws Exception {
        EventRequestDto request = EventRequestDto.builder()
                .eventType("PAGE_VIEW")
                .clientId("client-1")
                .build();

        when(validationService.validate(any()))
                .thenThrow(new EventTypeValidationException(Collections.singletonList("page is required for PAGE_VIEW event")));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.errors[0]").value("page is required for PAGE_VIEW event"));
    }

    @Test
    void testIngestEventBrokerFailureReturns503WithRetryAfter() throws Exception {
        EventRequestDto request = EventRequestDto.builder()
                .eventType("LOGIN")
                .clientId("client-1")
                .build();

        AnalyticsEvent enriched = AnalyticsEvent.builder()
                .eventId("3f2b8c1e-5a0d-4e7b-9c1a-2d6f4b8e1a90")
                .eventType("LOGIN")
                .clientId("client-1")
                .receivedAt(Instant.now())
                .build();

        when(validationService.validate(any())).thenReturn(EventType.LOGIN);
        when(enrichmentService.enrich(any(), any())).thenReturn(enriched);
        when(producerService.sendEvent(any()))
                .thenThrow(new KafkaPublishException("Kafka broker did not acknowledge event within 3s", "timeout"));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.title").value("Kafka Service Unavailable"))
                .andExpect(jsonPath("$.reason").value("timeout"));
    }

    @Test
    void testBatchIngestionReturnsAcceptedAndRejectedSummary() throws Exception {
        EventRequestDto validDto = EventRequestDto.builder()
                .eventType("PAGE_VIEW")
                .clientId("client-1")
                .page("/home")
                .build();

        EventRequestDto invalidDto = EventRequestDto.builder()
                .eventType("PURCHASE")
                .clientId("client-2")
                // Missing productId
                .build();

        BatchEventRequestDto batch = BatchEventRequestDto.builder()
                .events(List.of(validDto, invalidDto))
                .build();

        when(validationService.validate(validDto)).thenReturn(EventType.PAGE_VIEW);
        when(enrichmentService.enrich(any(), any())).thenReturn(AnalyticsEvent.builder().eventId("id-1").build());

        when(validationService.validate(invalidDto))
                .thenThrow(new EventTypeValidationException(Collections.singletonList("productId is required for PURCHASE event")));

        mockMvc.perform(post("/api/events/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected[0].index").value(1))
                .andExpect(jsonPath("$.rejected[0].errors[0]").value("productId is required for PURCHASE event"));
    }
}
