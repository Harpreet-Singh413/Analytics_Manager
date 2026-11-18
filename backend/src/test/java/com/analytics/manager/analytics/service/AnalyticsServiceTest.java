package com.analytics.manager.analytics.service;

import com.analytics.manager.analytics.dto.*;
import com.analytics.manager.common.entity.ClientStatsEntity;
import com.analytics.manager.common.entity.PageStatsEntity;
import com.analytics.manager.common.repository.ClientStatsRepository;
import com.analytics.manager.common.repository.EventAggMinuteRepository;
import com.analytics.manager.common.repository.EventRepository;
import com.analytics.manager.common.repository.FailedEventRepository;
import com.analytics.manager.common.repository.PageStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private EventAggMinuteRepository eventAggMinuteRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ClientStatsRepository clientStatsRepository;

    @Mock
    private PageStatsRepository pageStatsRepository;

    @Mock
    private FailedEventRepository failedEventRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
                eventAggMinuteRepository,
                eventRepository,
                clientStatsRepository,
                pageStatsRepository,
                failedEventRepository
        );
    }

    @Test
    void testGetSummaryAggregatesCorrectly() {
        when(eventAggMinuteRepository.getTotalEventsCount()).thenReturn(5000L);
        when(eventAggMinuteRepository.getEventsCountSince(any())).thenReturn(120L, 450L);
        when(eventRepository.countActiveClientsSince(any())).thenReturn(35L);
        when(failedEventRepository.count()).thenReturn(3L);
        when(eventRepository.findAvgProcessingLatencyMsSince(any())).thenReturn(24.567);

        AnalyticsSummaryDto summary = analyticsService.getSummary();

        assertNotNull(summary);
        assertEquals(5000L, summary.getTotalEvents());
        assertEquals(120L, summary.getEventsLastMinute());
        assertEquals(450L, summary.getEventsLast5Minutes());
        assertEquals(35L, summary.getActiveClients());
        assertEquals(3L, summary.getFailedEvents());
        assertEquals(24.57, summary.getAvgProcessingLatencyMs());
    }

    @Test
    void testGetEventsByType() {
        EventAggMinuteRepository.EventTypeCount countMock = new EventAggMinuteRepository.EventTypeCount() {
            @Override
            public String getEventType() {
                return "PAGE_VIEW";
            }

            @Override
            public Long getCount() {
                return 1500L;
            }
        };

        when(eventAggMinuteRepository.getCountsByEventType(any(), any()))
                .thenReturn(Collections.singletonList(countMock));

        List<EventTypeCountDto> list = analyticsService.getEventsByType(null, null);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("PAGE_VIEW", list.get(0).getEventType());
        assertEquals(1500L, list.get(0).getCount());
    }

    @Test
    void testGetEventsOverTime() {
        Instant bucket = Instant.now();
        EventAggMinuteRepository.BucketCount bucketMock = new EventAggMinuteRepository.BucketCount() {
            @Override
            public Instant getBucketStart() {
                return bucket;
            }

            @Override
            public Long getCount() {
                return 42L;
            }
        };

        when(eventAggMinuteRepository.getEventsOverTime(any(), any()))
                .thenReturn(Collections.singletonList(bucketMock));

        List<EventsOverTimeDto> list = analyticsService.getEventsOverTime(null, null, "minute");

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(bucket, list.get(0).getBucketStart());
        assertEquals(42L, list.get(0).getCount());
    }

    @Test
    void testGetTopClients() {
        ClientStatsEntity client = ClientStatsEntity.builder()
                .clientId("client-vip")
                .totalEvents(999L)
                .firstSeen(Instant.now().minus(Duration.ofDays(1)))
                .lastSeen(Instant.now())
                .build();

        when(clientStatsRepository.findAllByOrderByTotalEventsDesc(any()))
                .thenReturn(Collections.singletonList(client));

        List<ClientStatDto> list = analyticsService.getTopClients(10, "count");

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("client-vip", list.get(0).getClientId());
        assertEquals(999L, list.get(0).getTotalEvents());
    }

    @Test
    void testGetTopPages() {
        PageStatsEntity page = PageStatsEntity.builder()
                .page("/products/popular-item")
                .viewCount(780L)
                .build();

        when(pageStatsRepository.findAllByOrderByViewCountDesc(any()))
                .thenReturn(Collections.singletonList(page));

        List<PageStatDto> list = analyticsService.getTopPages(5);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("/products/popular-item", list.get(0).getPage());
        assertEquals(780L, list.get(0).getViews());
    }
}
