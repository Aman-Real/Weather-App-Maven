package com.skycast.common.dto;

/** A place returned by geocoding search or stored as a favourite. */
public record LocationDto(String name, String country, String admin1, double latitude, double longitude) {
}
