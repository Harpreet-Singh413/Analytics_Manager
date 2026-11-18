package com.analytics.manager.ingestion.validation;

import com.analytics.manager.common.model.EventType;
import com.analytics.manager.ingestion.dto.EventRequestDto;
import com.analytics.manager.ingestion.validation.impl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventValidationServiceTest {

    private EventValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new EventValidationService(
                Arrays.asList(
                        new PageViewValidator(),
                        new LoginValidator(),
                        new ProductViewValidator(),
                        new SearchValidator(),
                        new PurchaseValidator(),
                        new ErrorValidator()
                ),
                new ObjectMapper()
        );
    }

    @Test
    void testValidPageViewEvent() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventType("PAGE_VIEW")
                .clientId("client-123")
                .page("/home")
                .build();

        EventType type = validationService.validate(dto);
        assertEquals(EventType.PAGE_VIEW, type);
    }

    @Test
    void testUnknownEventTypeThrows400() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventType("UNKNOWN_TYPE")
                .clientId("client-123")
                .build();

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(dto));
    }

    @Test
    void testInvalidUuidFormatThrows400() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventId("not-a-valid-uuid")
                .eventType("LOGIN")
                .clientId("client-123")
                .build();

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(dto));
    }

    @Test
    void testFutureOccurredAtThrows400() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventType("LOGIN")
                .clientId("client-123")
                .occurredAt(Instant.now().plus(Duration.ofMinutes(10)))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(dto));
    }

    @Test
    void testTooOldOccurredAtThrows400() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventType("LOGIN")
                .clientId("client-123")
                .occurredAt(Instant.now().minus(Duration.ofDays(35)))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(dto));
    }

    @Test
    void testMetadataTooManyKeysThrows400() {
        Map<String, Object> meta = new HashMap<>();
        for (int i = 0; i < 25; i++) {
            meta.put("key" + i, "val" + i);
        }

        EventRequestDto dto = EventRequestDto.builder()
                .eventType("LOGIN")
                .clientId("client-123")
                .metadata(meta)
                .build();

        assertThrows(IllegalArgumentException.class, () -> validationService.validate(dto));
    }

    @Test
    void testPageViewMissingPageThrows422() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventType("PAGE_VIEW")
                .clientId("client-123")
                .page(null)
                .build();

        EventTypeValidationException ex = assertThrows(EventTypeValidationException.class,
                () -> validationService.validate(dto));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("page is required")));
    }

    @Test
    void testProductViewMissingProductIdThrows422() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventType("PRODUCT_VIEW")
                .clientId("client-123")
                .page("/products/item-1")
                .productId(null)
                .build();

        EventTypeValidationException ex = assertThrows(EventTypeValidationException.class,
                () -> validationService.validate(dto));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("productId is required")));
    }

    @Test
    void testPurchaseMissingProductIdThrows422() {
        EventRequestDto dto = EventRequestDto.builder()
                .eventType("PURCHASE")
                .clientId("client-123")
                .productId(null)
                .build();

        EventTypeValidationException ex = assertThrows(EventTypeValidationException.class,
                () -> validationService.validate(dto));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("productId is required")));
    }
}
