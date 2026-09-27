package fpgata.model;

import java.util.Locale;
import java.util.Objects;

/**
 * 领域量（时间）：原始表示 + 单位 + 规范化值(ps)。
 * 无法转换时 normalized 为 NaN，diagnostic 给出原因；
 * 原值始终保留，不丢数据、不臆造。
 */
public final class Quantity {
    public final String raw;        // 导入时的原始表示，例如 "1.250 ns" / "3.400" / "bogus"
    public final String unit;       // 原单位（推断或显式），未知为 null
    public final double normalizedPs; // 规范化到皮秒；NaN 表示无法转换
    public final String diagnostic; // 无法转换时的原因，可转换为 null

    private Quantity(String raw, String unit, double ps, String diagnostic) {
        this.raw = raw;
        this.unit = unit;
        this.normalizedPs = ps;
        this.diagnostic = diagnostic;
    }

    public static Quantity parse(String raw, String defaultUnit) {
        Objects.requireNonNull(raw, "raw");
        String s = raw.trim();
        String numPart = s;
        String unit = null;
        int k = s.indexOf(' ');
        if (k > 0) {
            numPart = s.substring(0, k).trim();
            unit = s.substring(k + 1).trim();
        } else {
            // 形如 1.250ns
            int cut = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isLetter(c)) {
                    cut = i;
                    break;
                }
            }
            if (cut > 0) {
                numPart = s.substring(0, cut);
                unit = s.substring(cut);
            } else {
                unit = defaultUnit;
            }
        }
        double factor = unitFactor(unit);
        try {
            double v = Double.parseDouble(numPart);
            if (Double.isNaN(factor)) {
                return new Quantity(s, unit, Double.NaN, "未知时间单位: " + unit);
            }
            return new Quantity(s, unit, v * factor, null);
        } catch (NumberFormatException e) {
            return new Quantity(s, unit, Double.NaN, "无法解析为数值: '" + numPart + "'");
        }
    }

    private static double unitFactor(String u) {
        if (u == null) return Double.NaN;
        switch (u.toLowerCase(Locale.ROOT)) {
            case "ps": return 1.0;
            case "ns": return 1000.0;
            case "us": return 1_000_000.0;
            case "ms": return 1_000_000_000.0;
            default: return Double.NaN;
        }
    }

    public boolean isUsable() {
        return !Double.isNaN(normalizedPs);
    }

    public String formatPs() {
        if (!isUsable()) return "UNCONVERTIBLE:" + raw;
        if (normalizedPs == Math.rint(normalizedPs)) {
            return String.format(Locale.ROOT, "%.0f ps", normalizedPs);
        }
        return String.format(Locale.ROOT, "%.3f ps", normalizedPs);
    }

    @Override
    public String toString() {
        return raw + " -> " + formatPs();
    }
}
