package com.timing.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A physical quantity carrying its original textual representation and unit.
 * The normalized numeric layer ({@link #normalizedNs()} / {@link #normalizedMhz()})
 * is derived; parsing failures keep the raw token in place with a diagnostic
 * instead of dropping the value for the sake of a chart.
 */
public final class Physical {

    public enum Unit {
        NS("ns", 1.0),
        PS("ps", 0.001),
        US("us", 1000.0),
        MHZ("MHz", 1.0),
        KHZ("kHz", 0.001),
        HZ("Hz", 0.000001),
        UNKNOWN("?", 1.0);

        public final String label;
        public final double toBase;

        Unit(String label, double toBase) {
            this.label = label;
            this.toBase = toBase;
        }

        public boolean isTime() {
            return this == NS || this == PS || this == US;
        }

        public boolean isFrequency() {
            return this == MHZ || this == KHZ || this == HZ;
        }

        public static Unit fromToken(String token) {
            for (Unit u : values()) {
                if (u.label.equalsIgnoreCase(token)) {
                    return u;
                }
            }
            return UNKNOWN;
        }
    }

    private final String raw;
    private final Double value;
    private final Unit unit;
    private final String diagnostic;

    private Physical(String raw, Double value, Unit unit, String diagnostic) {
        this.raw = raw;
        this.value = value;
        this.unit = unit;
        this.diagnostic = diagnostic;
    }

    public static Physical parse(String token) {
        Objects.requireNonNull(token, "token");
        String t = token.trim();
        if (t.isEmpty()) {
            return new Physical(token, null, Unit.UNKNOWN, "empty physical token");
        }
        int cut = t.length();
        while (cut > 0 && !Character.isDigit(t.charAt(cut - 1)) && t.charAt(cut - 1) != '.') {
            cut--;
        }
        if (cut == 0) {
            return new Physical(token, null, Unit.UNKNOWN, "no numeric part in '" + token + "'");
        }
        String num = t.substring(0, cut);
        String unitToken = t.substring(cut).trim();
        Unit unit = Unit.fromToken(unitToken);
        if (unit == Unit.UNKNOWN) {
            return new Physical(token, null, Unit.UNKNOWN, "unknown unit '" + unitToken + "'");
        }
        try {
            double v = Double.parseDouble(num);
            return new Physical(token, v, unit, null);
        } catch (NumberFormatException e) {
            return new Physical(token, null, unit, "invalid number '" + num + "'");
        }
    }

    public static Physical ofNs(double ns) {
        String text = trim(ns) + "ns";
        return new Physical(text, ns, Unit.NS, null);
    }

    public static Physical rawOnly(String token, String diagnostic) {
        return new Physical(token, null, Unit.UNKNOWN, diagnostic);
    }

    /** Normalized value in nanoseconds; null when not convertible. */
    public Double normalizedNs() {
        if (value == null || !unit.isTime()) {
            return null;
        }
        return value * unit.toBase;
    }

    /** Normalized value in MHz; null when not convertible. */
    public Double normalizedMhz() {
        if (value == null || !unit.isFrequency()) {
            return null;
        }
        return value * unit.toBase;
    }

    public boolean convertible() {
        return value != null && unit != Unit.UNKNOWN;
    }

    public String raw() {
        return raw;
    }

    public Double value() {
        return value;
    }

    public Unit unit() {
        return unit;
    }

    public String diagnostic() {
        return diagnostic;
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) {
            return String.format(Locale.ROOT, "%.1f", v);
        }
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public String toString() {
        return raw;
    }
}
