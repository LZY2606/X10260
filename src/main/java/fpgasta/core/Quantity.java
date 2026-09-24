package fpgasta.core;

import java.util.Locale;
import java.util.Objects;

/**
 * A physical quantity that always carries its unit / coordinate basis.
 * The original representation is retained verbatim; normalization happens only in the
 * derived layer. Unconvertible values stay at the origin with a diagnostic.
 */
public final class Quantity {
    public static final String NS = "ns";
    public static final String PS = "ps";
    public static final String UNKNOWN = "unknown";

    private final String raw;
    private final double valueNs;
    private final String unit;
    private final boolean convertible;
    private final String diagnostic;

    private Quantity(String raw, double valueNs, String unit, boolean convertible, String diagnostic) {
        this.raw = raw;
        this.valueNs = valueNs;
        this.unit = unit;
        this.convertible = convertible;
        this.diagnostic = diagnostic;
    }

    public static Quantity time(String raw) {
        Objects.requireNonNull(raw, "raw quantity");
        String s = raw.trim();
        try {
            if (s.endsWith(NS)) {
                double v = Double.parseDouble(s.substring(0, s.length() - 2).trim());
                return new Quantity(raw, v, NS, true, null);
            }
            if (s.endsWith(PS)) {
                double v = Double.parseDouble(s.substring(0, s.length() - 2).trim());
                return new Quantity(raw, v / 1000.0, PS, true, null);
            }
            double v = Double.parseDouble(s);
            return new Quantity(raw, v, NS, true, "no unit; assumed ns");
        } catch (NumberFormatException e) {
            return new Quantity(raw, Double.NaN, UNKNOWN, false,
                    "cannot convert to ns: " + s);
        }
    }

    public static Quantity nanos(double v) {
        return new Quantity(String.format(Locale.ROOT, "%.3fns", v), v, NS, true, null);
    }

    public String raw() { return raw; }
    public double ns() { return valueNs; }
    public String unit() { return unit; }
    public boolean convertible() { return convertible; }
    public String diagnostic() { return diagnostic; }

    @Override public String toString() { return raw; }
}
