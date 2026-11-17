package com.analytics.manager.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "event_agg_minute")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventAggMinuteEntity {

    @EmbeddedId
    private EventAggMinuteId id;

    @Column(name = "event_count", nullable = false)
    private Long eventCount;
}
