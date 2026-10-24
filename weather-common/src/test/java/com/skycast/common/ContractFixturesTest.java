package com.skycast.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.skycast.common.dto.AlertDto;
import com.skycast.common.dto.AlertRuleDto;
import com.skycast.common.dto.AuthResponse;
import com.skycast.common.dto.CurrentWeatherDto;
import com.skycast.common.dto.FavoriteDto;
import com.skycast.common.dto.ForecastDto;
import com.skycast.common.dto.HistoryDto;
import com.skycast.common.dto.LocationDto;
import com.skycast.common.dto.MapPointDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same JSON files feed the frontend e2e mock API (verify/mock-server.mjs).
 * Each fixture must deserialize into the backend DTO with no unknown fields and
 * serialize back to identical JSON, so frontend and backend cannot drift apart.
 */
class ContractFixturesTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("current.json", CurrentWeatherDto.class, false),
                Arguments.of("forecast.json", ForecastDto.class, false),
                Arguments.of("history.json", HistoryDto.class, false),
                Arguments.of("map.json", MapPointDto.class, true),
                Arguments.of("geocode.json", LocationDto.class, true),
                Arguments.of("alerts.json", AlertDto.class, true),
                Arguments.of("alert-event.json", AlertDto.class, false),
                Arguments.of("alert-rules.json", AlertRuleDto.class, true),
                Arguments.of("favorites.json", FavoriteDto.class, true),
                Arguments.of("auth.json", AuthResponse.class, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixtureRoundTripsThroughDto(String file, Class<?> type, boolean list) throws Exception {
        JsonNode original;
        try (InputStream in = getClass().getResourceAsStream("/contracts/" + file)) {
            assertThat(in).as("fixture %s exists", file).isNotNull();
            original = mapper.readTree(in);
        }
        JavaType javaType = list
                ? mapper.getTypeFactory().constructCollectionType(List.class, type)
                : mapper.getTypeFactory().constructType(type);

        Object dto = mapper.treeToValue(original, javaType);
        JsonNode roundTrip = mapper.valueToTree(dto);

        assertThat(roundTrip.equals(NUMERIC_AWARE, original))
                .as("%s must match the DTO exactly.%nexpected: %s%nactual:   %s", file, original, roundTrip)
                .isTrue();
    }

    /** 12 and 12.0 are the same number; "10:00" and "10:00:00" are the same time. */
    private static final Comparator<JsonNode> NUMERIC_AWARE = (a, b) -> {
        if (a.isNumber() && b.isNumber()) {
            return Double.compare(a.doubleValue(), b.doubleValue());
        }
        if (a.isTextual() && b.isTextual() && !a.asText().equals(b.asText())) {
            try {
                return LocalDateTime.parse(a.asText()).equals(LocalDateTime.parse(b.asText())) ? 0 : 1;
            } catch (DateTimeParseException e) {
                return 1;
            }
        }
        return a.equals(b) ? 0 : 1;
    };
}
