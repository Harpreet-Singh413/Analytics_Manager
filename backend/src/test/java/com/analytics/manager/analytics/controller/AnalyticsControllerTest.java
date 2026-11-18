package com.analytics.manager.analytics.controller;

import com.analytics.manager.analytics.dto.*;
import com.analytics.manager.analytics.service.AnalyticsService;
import com.analytics.manager.ingestion.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@Import(GlobalExceptionHandler.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    void testGetSummaryReturns200() throws Exception {
        AnalyticsSummaryDto summary = AnalyticsSummaryDto.builder()
                .totalEvents(1000L)
                .eventsLastMinute(20L)
                .eventsLast5Minutes(95L)
                .activeClients(12L)
                .failedEvents(2L)
                .avgProcessingLatencyMs(15.4)
                .build();

        when(analyticsService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/analytics/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1000))
                .andExpect(jsonPath("$.eventsLastMinute").value(20))
                .andExpect(jsonPath("$.activeClients").value(12))
                .andExpect(jsonPath("$.avgProcessingLatencyMs").value(15.4));
    }

    @Test
    void testGetEventsByTypeReturns200() throws Exception {
        EventTypeCountDto dto = new EventTypeCountDto("PAGE_VIEW", 400L);
        when(analyticsService.getEventsByType(any(), any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/analytics/events-by-type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("PAGE_VIEW"))
                .andExpect(jsonPath("$[0].count").value(400));
    }

    @Test
    void testGetEventsOverTimeReturns200() throws Exception {
        EventsOverTimeDto dto = new EventsOverTimeDto(Instant.now(), 55L);
        when(analyticsService.getEventsOverTime(any(), any(), eq("minute"))).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/analytics/events-over-time?interval=minute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(55));
    }

    @Test
    void testGetTopClientsReturns200() throws Exception {
        ClientStatDto client = ClientStatDto.builder()
                .clientId("user-1")
                .totalEvents(150L)
                .firstSeen(Instant.now())
                .lastSeen(Instant.now())
                .build();

        when(analyticsService.getTopClients(eq(5), eq("count"))).thenReturn(List.of(client));

        mockMvc.perform(get("/api/analytics/clients?limit=5&sort=count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value("user-1"))
                .andExpect(jsonPath("$[0].totalEvents").value(150));
    }

    @Test
    void testGetTopPagesReturns200() throws Exception {
        PageStatDto page = new PageStatDto("/checkout", 300L);
        when(analyticsService.getTopPages(eq(5))).thenReturn(List.of(page));

        mockMvc.perform(get("/api/analytics/top-pages?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].page").value("/checkout"))
                .andExpect(jsonPath("$[0].views").value(300));
    }
}
