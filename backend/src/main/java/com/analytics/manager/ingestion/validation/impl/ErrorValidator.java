package com.analytics.manager.ingestion.validation.impl;

import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.analytics.manager.ingestion.validation.EventTypeValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ErrorValidator implements EventTypeValidator {

    @Override
    public EventType supportedType() {
        return EventType.ERROR;
    }

    @Override
    public List<String> validate(EventRequestDto dto) {
        return Collections.emptyList();
    }
}
