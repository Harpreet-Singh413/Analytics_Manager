package com.analytics.manager.ingestion.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEventRequestDto {

    @NotEmpty(message = "events list must not be empty")
    @Size(max = 100, message = "batch cannot contain more than 100 events")
    private List<EventRequestDto> events;
}
