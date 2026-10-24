package com.skycast.common.dto;

/** One grid point of the interactive map's temperature / wind layer. */
public record MapPointDto(double latitude, double longitude, double temperature,
                          double windSpeed, int windDirection, int weatherCode) {
}
