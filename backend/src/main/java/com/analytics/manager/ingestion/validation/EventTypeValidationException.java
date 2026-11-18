package com.analytics.manager.ingestion.validation;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class EventTypeValidationException extends RuntimeException {
    private final List<String> errors;

    public EventTypeValidationException(String message) {
        super(message);
        this.errors = Collections.singletonList(message);
    }

    public EventTypeValidationException(List<String> errors) {
        super(String.join(", ", errors));
        this.errors = errors;
    }
}
