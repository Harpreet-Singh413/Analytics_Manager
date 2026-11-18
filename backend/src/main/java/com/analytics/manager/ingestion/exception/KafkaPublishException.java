package com.analytics.manager.ingestion.exception;

import lombok.Getter;

@Getter
public class KafkaPublishException extends RuntimeException {
    private final String reason;

    public KafkaPublishException(String message, String reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public KafkaPublishException(String message, String reason) {
        super(message);
        this.reason = reason;
    }
}
