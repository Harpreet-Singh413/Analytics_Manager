package com.analytics.manager.analytics.service;

import com.analytics.manager.analytics.dto.*;
import com.analytics.manager.common.entity.ClientStatsEntity;
import com.analytics.manager.common.entity.PageStatsEntity;
import com.analytics.manager.common.repository.ClientStatsRepository;
import com.analytics.manager.common.repository.EventAggMinuteRepository;
import com.analytics.manager.common.repository.EventRepository;
import com.analytics.manager.common.repository.PageStatsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalyticsService {

    private static final long MAX_RANGE_DAYS = 7;

    private final EventAggMinuteRepository eventAggMinuteRepository;
    private final EventRepository eventRepository;
    private final ClientStatsRepository clientStatsRepository;
    private final PageStatsRepository pageStatsRepository;
    private final com.analytics.manager.common.repository.FailedEventRepository failedEventRepository;

    public AnalyticsService(EventAggMinuteRepository eventAggMinuteRepository,
                            EventRepository eventRepository,
                            ClientStatsRepository clientStatsRepository,
                            PageStatsRepository pageStatsRepository,
                            com.analytics.manager.common.repository.FailedEventRepository failedEventRepository) {
        this.eventAggMinuteRepository = eventAggMinuteRepository;
        this.eventRepository = eventRepository;
        this.clientStatsRepository = clientStatsRepository;
        this.pageStatsRepository = pageStatsRepository;
        this.failedEventRepository = failedEventRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary() {
        Instant now = Instant.now();
        Instant oneMinAgo = now.minus(Duration.ofMinutes(1));
        Instant fiveMinAgo = now.minus(Duration.ofMinutes(5));

        long totalEvents = eventAggMinuteRepository.getTotalEventsCount();
        long eventsLastMinute = eventAggMinuteRepository.getEventsCountSince(oneMinAgo);
        long eventsLast5Minutes = eventAggMinuteRepository.getEventsCountSince(fiveMinAgo);
        long activeClients = eventRepository.countActiveClientsSince(fiveMinAgo);
        long failedEvents = failedEventRepository.count();
        Double avgLatency = eventRepository.findAvgProcessingLatencyMsSince(fiveMinAgo);

        return AnalyticsSummaryDto.builder()
                .totalEvents(totalEvents)
                .eventsLastMinute(eventsLastMinute)
                .eventsLast5Minutes(eventsLast5Minutes)
                .activeClients(activeClients)
                .failedEvents(failedEvents)
                .avgProcessingLatencyMs(avgLatency != null ? Math.round(avgLatency * 100.0) / 100.0 : 0.0)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EventTypeCountDto> getEventsByType(Instant from, Instant to) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofHours(24));
        validateTimeRange(effectiveFrom, effectiveTo);

        List<EventAggMinuteRepository.EventTypeCount> results =
                eventAggMinuteRepository.getCountsByEventType(effectiveFrom, effectiveTo);

        return results.stream()
                .map(r -> new EventTypeCountDto(r.getEventType(), r.getCount()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventsOverTimeDto> getEventsOverTime(Instant from, Instant to, String interval) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofMinutes(30));
        validateTimeRange(effectiveFrom, effectiveTo);

        List<EventAggMinuteRepository.BucketCount> results =
                eventAggMinuteRepository.getEventsOverTime(effectiveFrom, effectiveTo);

        return results.stream()
                .map(r -> new EventsOverTimeDto(r.getBucketStart(), r.getCount()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClientStatDto> getTopClients(int limit, String sort) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        List<ClientStatsEntity> clients;

        if ("lastSeen".equalsIgnoreCase(sort)) {
            clients = clientStatsRepository.findAllByOrderByLastSeenDesc(PageRequest.of(0, effectiveLimit));
        } else {
            clients = clientStatsRepository.findAllByOrderByTotalEventsDesc(PageRequest.of(0, effectiveLimit));
        }

        return clients.stream()
                .map(c -> ClientStatDto.builder()
                        .clientId(c.getClientId())
                        .totalEvents(c.getTotalEvents())
                        .firstSeen(c.getFirstSeen())
                        .lastSeen(c.getLastSeen())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PageStatDto> getTopPages(int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 50);
        List<PageStatsEntity> pages = pageStatsRepository.findAllByOrderByViewCountDesc(PageRequest.of(0, effectiveLimit));

        return pages.stream()
                .map(p -> PageStatDto.builder()
                        .page(p.getPage())
                        .views(p.getViewCount())
                        .build())
                .collect(Collectors.toList());
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' timestamp cannot be after 'to' timestamp");
        }
        if (Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Requested range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }
}
