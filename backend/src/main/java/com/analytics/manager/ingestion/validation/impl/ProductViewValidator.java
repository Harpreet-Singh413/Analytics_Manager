package com.analytics.manager.ingestion.validation.impl;

import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.analytics.manager.ingestion.validation.EventTypeValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProductViewValidator implements EventTypeValidator {

    @Override
    public EventType supportedType() {
        return EventType.PRODUCT_VIEW;
    }

    @Override
    public List<String> validate(EventRequestDto dto) {
        List<String> errors = new ArrayList<>();
        if (dto.getPage() == null || dto.getPage().trim().isEmpty()) {
            errors.add("page is required for PRODUCT_VIEW event");
        }
        if (dto.getProductId() == null || dto.getProductId().trim().isEmpty()) {
            errors.add("productId is required for PRODUCT_VIEW event");
        }
        return errors;
    }
}
