package com.analytics.manager.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "page_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageStatsEntity {

    @Id
    @Column(name = "page", nullable = false, length = 255)
    private String page;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;
}
