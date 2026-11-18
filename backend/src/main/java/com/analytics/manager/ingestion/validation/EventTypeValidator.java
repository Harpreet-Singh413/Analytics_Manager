package com.analytics.manager.ingestion.validation;

import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;

import java.util.List;

public interface EventTypeValidator {
    EventType supportedType();
    List<String> validate(EventRequestDto dto);
}
