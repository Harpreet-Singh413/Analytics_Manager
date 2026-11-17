package com.analytics.manager.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventAggMinuteId implements Serializable {

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
}
