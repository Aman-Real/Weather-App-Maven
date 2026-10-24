package com.skycast.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skycast.weather.client.OpenMeteoModels;
import com.skycast.weather.support.WeatherCodeTranslator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Loads the recorded Open-Meteo responses in src/test/resources/openmeteo. */
public final class TestData {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private TestData() {
    }

    public static String raw(String file) {
        try (InputStream in = TestData.class.getResourceAsStream("/openmeteo/" + file)) {
            if (in == null) {
                throw new IllegalArgumentException("missing test file " + file);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static OpenMeteoModels.Forecast forecast(String file) {
        try {
            return JSON.readValue(raw(file), OpenMeteoModels.Forecast.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static WeatherCodeTranslator translator() {
        WeatherCodeTranslator t = new WeatherCodeTranslator();
        t.setDescriptions(Map.of(0, "Clear sky", 1, "Mainly clear", 2, "Partly cloudy", 3, "Overcast",
                61, "Slight rain", 63, "Moderate rain", 95, "Thunderstorm"));
        return t;
    }
}
