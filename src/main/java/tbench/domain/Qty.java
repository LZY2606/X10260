package tbench.domain;

import java.util.Objects;

/**
 * A physical quantity with the original textual representation preserved.
 * Normalisation to a canonical unit happens only in the derived layer; if a
 * value cannot be converted, {@link #error} keeps it in place instead of
 * dropping it for the sake of drawing.
 */
public final class Qty {
    public final String raw;
    public final String unit;
    public final double value;
    public final String canonicalUnit;
    public final String error;

    private Qty(String raw, String unit, double value, String canonicalUnit, String error) {
        this.raw = raw;
        this.unit = unit;
        this.value = value;
        this.canonicalUnit = canonicalUnit;
        this.error = error;
    }

    public static Qty time(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return new Qty(raw, "", Double.NaN, "ns", "empty quantity");
        }
        int cut = 0;
        while (cut < trimmed.length()
                && (Character.isDigit(trimmed.charAt(cut))
                    || trimmed.charAt(cut) == '.'
                    || trimmed.charAt(cut) == '-'
                    || trimmed.charAt(cut) == '+'
                    || trimmed.charAt(cut) == 'e'
                    || trimmed.charAt(cut) == 'E')) {
            cut++;
        }
        String numPart = trimmed.substring(0, cut).trim();
        String unitPart = trimmed.substring(cut).trim();
        double v;
        try {
            v = Double.parseDouble(numPart);
        } catch (NumberFormatException e) {
            return new Qty(raw, unitPart, Double.NaN, "ns",
                    "cannot parse numeric part: '" + numPart + "'");
        }
        double ns;
        String lower = unitPart.toLowerCase();
        switch (lower) {
            case "":
            case "ns":
                ns = v;
                break;
            case "ps":
                ns = v / 1000.0;
                break;
            case "us":
            case "µs":
                ns = v * 1000.0;
                break;
            default:
                return new Qty(raw, unitPart, Double.NaN, "ns",
                        "unknown time unit: '" + unitPart + "' (known: ps, ns, us)");
        }
        return new Qty(raw, unitPart, ns, "ns", null);
    }

    public boolean convertible() {
        return error == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Qty)) {
            return false;
        }
        Qty qty = (Qty) o;
        return Double.compare(qty.value, value) == 0
                && Objects.equals(canonicalUnit, qty.canonicalUnit)
                && Objects.equals(raw, qty.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, value, canonicalUnit);
    }

    @Override
    public String toString() {
        return raw;
    }
}
