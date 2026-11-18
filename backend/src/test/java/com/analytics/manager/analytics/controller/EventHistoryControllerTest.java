package com.analytics.manager.analytics.controller;

import com.analytics.manager.analytics.dto.EventDetailDto;
import com.analytics.manager.analytics.dto.FailedEventDto;
import com.analytics.manager.analytics.dto.PageResponseDto;
import com.analytics.manager.analytics.service.EventHistoryService;
import com.analytics.manager.common.exception.ResourceNotFoundException;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventHistoryController.class)
@Import(GlobalExceptionHandler.class)
class EventHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventHistoryService eventHistoryService;

    @Test
    void testGetEventsReturns200WithPagination() throws Exception {
        EventDetailDto dto = EventDetailDto.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAGE_VIEW")
                .clientId("client-1")
                .status("PROCESSED")
                .occurredAt(Instant.now())
                .build();

        PageResponseDto<EventDetailDto> pageResponse = PageResponseDto.<EventDetailDto>builder()
                .content(Collections.singletonList(dto))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(eventHistoryService.getEvents(any(), any(), any(), any(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("PAGE_VIEW"))
                .andExpect(jsonPath("$.content[0].status").value("PROCESSED"));
    }

    @Test
    void testGetEventsPageSizeOverLimitReturns400() throws Exception {
        mockMvc.perform(get("/api/events?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void testGetEventByIdSuccess() throws Exception {
        String eventId = UUID.randomUUID().toString();
        EventDetailDto dto = EventDetailDto.builder()
                .eventId(eventId)
                .eventType("LOGIN")
                .clientId("client-2")
                .status("PROCESSED")
                .build();

        when(eventHistoryService.getEventById(eventId)).thenReturn(dto);

        mockMvc.perform(get("/api/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void testGetEventByIdNotFoundReturns404() throws Exception {
        String eventId = UUID.randomUUID().toString();
        when(eventHistoryService.getEventById(eventId))
                .thenThrow(new ResourceNotFoundException("Event not found"));

        mockMvc.perform(get("/api/events/" + eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void testGetFailedEventsReturns200() throws Exception {
        FailedEventDto failed = FailedEventDto.builder()
                .id(1L)
                .eventId("test-uuid")
                .errorClass("IllegalArgumentException")
                .errorMessage("Invalid format")
                .build();

        PageResponseDto<FailedEventDto> pageResponse = PageResponseDto.<FailedEventDto>builder()
                .content(Collections.singletonList(failed))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(eventHistoryService.getFailedEvents(any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/events/failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("test-uuid"))
                .andExpect(jsonPath("$.content[0].errorClass").value("IllegalArgumentException"));
    }
}
