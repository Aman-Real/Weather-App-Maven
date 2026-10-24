package com.skycast.common.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Alert subscription. metric: TEMPERATURE_ABOVE, TEMPERATURE_BELOW, WIND_ABOVE, PRECIPITATION_ABOVE. */
public record AlertRuleDto(
        Long id,
        @NotBlank String locationName,
        @DecimalMin("-90") @DecimalMax("90") double latitude,
        @DecimalMin("-180") @DecimalMax("180") double longitude,
        @NotNull @Pattern(regexp = "TEMPERATURE_ABOVE|TEMPERATURE_BELOW|WIND_ABOVE|PRECIPITATION_ABOVE") String metric,
        double threshold,
        boolean active) {
}
