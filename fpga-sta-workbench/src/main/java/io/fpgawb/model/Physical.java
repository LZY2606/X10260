package io.fpgawb.model;

import java.util.Objects;

/**
 * A physical quantity that always preserves the original textual representation and its unit.
 * The import layer stores the raw token verbatim; the derived layer converts to a canonical
 * base (ps for time, Hz for frequency). A value that cannot be converted stays in place with a
 * diagnostic instead of being silently dropped.
 */
public final class Physical {
    public enum Kind { TIME, FREQUENCY, DIMENSIONLESS, UNPARSEABLE }

    private final String raw;
    private final Kind kind;
    private final double canonical; // ps, Hz, or 1
    private final String unit;

    private Physical(String raw, Kind kind, double canonical, String unit) {
        this.raw = raw;
        this.kind = kind;
        this.canonical = canonical;
        this.unit = unit;
    }

    public static Physical rawOnly(String raw, String diagnostic) {
        return new Physical(raw, Kind.UNPARSEABLE, Double.NaN, "");
    }

    public static Physical of(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) return new Physical(t, Kind.UNPARSEABLE, Double.NaN, "");
        int split = valueSplit(t);
        if (split <= 0) return new Physical(t, Kind.UNPARSEABLE, Double.NaN, "");
        double v;
        try {
            v = Double.parseDouble(t.substring(0, split).trim());
        } catch (NumberFormatException e) {
            return new Physical(t, Kind.UNPARSEABLE, Double.NaN, "");
        }
        String unit = t.substring(split).trim();
        Double ps = timeToPs(v, unit);
        if (ps != null) return new Physical(t, Kind.TIME, ps, unit);
        Double hz = freqToHz(v, unit);
        if (hz != null) return new Physical(t, Kind.FREQUENCY, hz, unit);
        if (unit.isEmpty() || "-".equals(unit)) {
            return new Physical(t, Kind.DIMENSIONLESS, v, unit);
        }
        return new Physical(t, Kind.UNPARSEABLE, Double.NaN, unit);
    }

    private static int valueSplit(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetter(c) || c == '%' || c == '°') {
                // allow no unit but scientific notation handled by digit/./e/-/+ only
                return i;
            }
        }
        return t.length();
    }

    private static Double timeToPs(double v, String u) {
        switch (u.toLowerCase()) {
            case "ps": return v;
            case "ns": return v * 1_000.0;
            case "us": case "µs": return v * 1_000_000.0;
            case "ms": return v * 1_000_000_000.0;
            default: return null;
        }
    }

    private static Double freqToHz(double v, String u) {
        switch (u.toLowerCase()) {
            case "hz": return v;
            case "khz": return v * 1e3;
            case "mhz": return v * 1e6;
            case "ghz": return v * 1e9;
            default: return null;
        }
    }

    public String raw() { return raw; }
    public Kind kind() { return kind; }
    public String unit() { return unit; }
    public boolean convertible() { return kind != Kind.UNPARSEABLE; }
    public double canonical() { return canonical; }
    public double psOrFail() {
        if (kind != Kind.TIME) throw new IllegalStateException("not a time: " + raw);
        return canonical;
    }
    public double hzOrFail() {
        if (kind != Kind.FREQUENCY) throw new IllegalStateException("not a frequency: " + raw);
        return canonical;
    }

    public static Physical ps(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        String s = fmt(r) + "ps";
        return new Physical(s, Kind.TIME, r, "ps");
    }

    static String fmt(double d) {
        if (d == Math.rint(d)) return Long.toString((long) d);
        return Double.toString(d);
    }

    @Override public String toString() { return raw; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Physical p)) return false;
        return Objects.equals(raw, p.raw);
    }
    @Override public int hashCode() { return raw.hashCode(); }
}
