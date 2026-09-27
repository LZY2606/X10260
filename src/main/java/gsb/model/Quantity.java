package gsb.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A domain quantity. The raw representation from the source document is always
 * preserved; normalization to picoseconds happens only in the derived layer.
 * Values that cannot be converted stay in place and carry a diagnostic.
 */
public record Quantity(String raw, String unit, Double ps, String diagnostic) {
    private static final Pattern P = Pattern.compile("^([-+0-9.eE]+)\\s*(ps|ns|us|ms)?$");

    public static Quantity parse(String raw, String defaultUnit) {
        if (raw == null || raw.isBlank()) {
            return new Quantity(raw, defaultUnit, null, "missing value");
        }
        Matcher m = P.matcher(raw.trim());
        if (!m.matches()) {
            return new Quantity(raw, null, null, "cannot parse quantity: " + raw);
        }
        String unit = m.group(2) != null ? m.group(2) : defaultUnit;
        if (unit == null) {
            return new Quantity(raw, null, null, "no unit for quantity: " + raw);
        }
        double factor = switch (unit) {
            case "ps" -> 1.0;
            case "ns" -> 1_000.0;
            case "us" -> 1_000_000.0;
            case "ms" -> 1_000_000_000.0;
            default -> -1.0;
        };
        if (factor < 0) {
            return new Quantity(raw, unit, null, "unknown unit: " + unit);
        }
        return new Quantity(raw, unit, Double.parseDouble(m.group(1)) * factor, null);
    }

    public boolean ok() {
        return ps != null;
    }

    public static String fmt(double ps) {
        String s = String.format("%.3f", ps);
        while (s.contains(".") && s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public String display() {
        StringBuilder b = new StringBuilder(raw == null ? "<missing>" : raw);
        if (unit != null) b.append(" ").append(unit);
        if (ps != null) b.append("  [").append(fmt(ps)).append(" ps]");
        if (diagnostic != null) b.append("  !! ").append(diagnostic);
        return b.toString();
    }
}
