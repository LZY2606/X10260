package stawb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A domain quantity that keeps its raw representation and unit (coordinate basis).
 * Normalization to picoseconds happens only in the derived layer; values that
 * cannot be converted stay in place with a diagnostic instead of being dropped.
 */
public record Quantity(String raw, String unit, Long ps, String diagnostic) {

    private static final Pattern PATTERN =
            Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]+)?\\s*$");

    public static Quantity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Quantity(raw, null, null, "MISSING_VALUE");
        }
        Matcher m = PATTERN.matcher(raw);
        if (!m.matches()) {
            return new Quantity(raw, null, null, "UNCONVERTIBLE: not a '<number> <unit>' literal");
        }
        String unit = m.group(2);
        if (unit == null) {
            return new Quantity(raw, null, null, "MISSING_UNIT: coordinate basis unknown");
        }
        unit = unit.toLowerCase();
        BigDecimal factor = switch (unit) {
            case "fs" -> new BigDecimal("0.001");
            case "ps" -> BigDecimal.ONE;
            case "ns" -> new BigDecimal("1000");
            case "us" -> new BigDecimal("1000000");
            case "ms" -> new BigDecimal("1000000000");
            default -> null;
        };
        if (factor == null) {
            return new Quantity(raw, unit, null, "UNCONVERTIBLE: unknown unit '" + unit + "'");
        }
        long ps = new BigDecimal(m.group(1)).multiply(factor)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new Quantity(raw, unit, ps, null);
    }

    public static Quantity ofPs(long ps) {
        return new Quantity(ps + "ps", "ps", ps, null);
    }

    public boolean convertible() {
        return ps != null;
    }

    public String display() {
        if (raw != null) return raw;
        return ps != null ? ps + "ps" : "<missing>";
    }
}
