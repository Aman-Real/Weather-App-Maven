package com.skycast.weather.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates WMO weather codes into text. Defined in beans.xml (XML configuration with
 * setter injection and init-method / destroy-method lifecycle callbacks) instead of @Component.
 */
public class WeatherCodeTranslator {

    private static final Logger log = LoggerFactory.getLogger(WeatherCodeTranslator.class);

    private Map<Integer, String> descriptions = new HashMap<>();
    private String unknownLabel = "Unknown";

    public void setDescriptions(Map<Integer, String> descriptions) {
        this.descriptions = new HashMap<>(descriptions);
    }

    public void setUnknownLabel(String unknownLabel) {
        this.unknownLabel = unknownLabel;
    }

    /** init-method: runs after properties are injected. */
    public void init() {
        log.info("WeatherCodeTranslator ready with {} WMO codes", descriptions.size());
    }

    /** destroy-method: runs when the context closes. */
    public void shutdown() {
        log.info("WeatherCodeTranslator shutting down");
    }

    public String describe(Integer code) {
        return code == null ? unknownLabel : descriptions.getOrDefault(code, unknownLabel);
    }

    public static boolean isSevere(int code) {
        return code >= 95;
    }
}
