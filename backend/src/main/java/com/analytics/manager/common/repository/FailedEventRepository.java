package com.analytics.manager.common.repository;

import com.analytics.manager.common.entity.FailedEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEventEntity, Long> {

    Optional<FailedEventEntity> findByEventId(String eventId);

    Page<FailedEventEntity> findAllByOrderByFailedAtDesc(Pageable pageable);
}
