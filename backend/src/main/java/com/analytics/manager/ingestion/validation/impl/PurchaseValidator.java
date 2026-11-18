package com.analytics.manager.ingestion.validation.impl;

import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.analytics.manager.ingestion.validation.EventTypeValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PurchaseValidator implements EventTypeValidator {

    @Override
    public EventType supportedType() {
        return EventType.PURCHASE;
    }

    @Override
    public List<String> validate(EventRequestDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getProductId() == null || dto.getProductId().trim().isEmpty()) {
            errors.add("productId is required for PURCHASE event");
        }
        return errors;
    }
}
