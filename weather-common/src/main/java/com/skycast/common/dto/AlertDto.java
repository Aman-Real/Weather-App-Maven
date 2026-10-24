package com.skycast.common.dto;

import java.time.LocalDateTime;

public record AlertDto(Long id, String locationName, double latitude, double longitude, String type,
                       String severity, String message, double observedValue, double threshold,
                       boolean acknowledged, LocalDateTime createdAt) {
}
