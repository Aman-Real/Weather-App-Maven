package com.skycast.weather.support;

import java.util.Locale;

/** Coordinate helpers: everything is rounded to 2 decimals (~1 km) so cache keys and DB rows line up. */
public final class Geo {

    private Geo() {
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Snap to a 0.5 degree grid so nearby map views share one cached grid. */
    public static double snapHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    public static String key(double lat, double lon) {
        return String.format(Locale.ROOT, "%.2f:%.2f", round2(lat), round2(lon));
    }

    public static String label(double lat, double lon) {
        return String.format(Locale.ROOT, "Lat %.2f, Lon %.2f", lat, lon);
    }
}
