package com.skycast.common.dto;

import java.time.LocalDateTime;

public record HourlyPoint(LocalDateTime time, double temperature, int precipitationProbability, int weatherCode) {
}
