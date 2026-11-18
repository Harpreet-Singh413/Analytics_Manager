package com.analytics.manager.ingestion.validation.impl;

import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.analytics.manager.ingestion.validation.EventTypeValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PageViewValidator implements EventTypeValidator {

    @Override
    public EventType supportedType() {
        return EventType.PAGE_VIEW;
    }

    @Override
    public List<String> validate(EventRequestDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getPage() == null || dto.getPage().trim().isEmpty()) {
            errors.add("page is required for PAGE_VIEW event");
        }
        return errors;
    }
}
