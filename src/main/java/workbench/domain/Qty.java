package workbench.domain;

/**
 * 领域量：携带单位与坐标基准，保留原始表示。
 * 无法转换的值 value=null，保留 raw 并附诊断（由调用方记录）。
 */
public record Qty(Double value, String unit, String raw, String basis) {

    public static Qty of(double value, String unit, String raw, String basis) {
        return new Qty(value, unit, raw, basis);
    }

    public static Qty unconvertible(String raw, String basis) {
        return new Qty(null, null, raw, basis);
    }

    public boolean convertible() {
        return value != null;
    }

    /** 解析 "9.482 ns" 这类文本；失败返回 unconvertible。 */
    public static Qty parse(String raw, String expectedUnit, String basis) {
        if (raw == null) {
            return unconvertible(null, basis);
        }
        String t = raw.trim();
        String[] parts = t.split("\\s+");
        try {
            if (parts.length == 1) {
                return new Qty(Double.parseDouble(parts[0]), expectedUnit, raw, basis);
            }
            String unit = parts[1];
            double v = Double.parseDouble(parts[0]);
            if (!unit.equals(expectedUnit)) {
                return unconvertible(raw, basis);
            }
            return new Qty(v, unit, raw, basis);
        } catch (NumberFormatException e) {
            return unconvertible(raw, basis);
        }
    }

    public String display() {
        if (!convertible()) {
            return "[unconvertible: " + raw + "]";
        }
        return String.format("%.3f %s", value, unit);
    }
}
