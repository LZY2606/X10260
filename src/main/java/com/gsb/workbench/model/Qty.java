package com.gsb.workbench.model;

import java.util.Locale;
import java.util.Objects;

/**
 * 领域量：始终保留原始表示与单位；规范化值（皮秒）只在派生层使用。
 * 无法转换时 valuePs 为 null，并携带诊断；原始值保留在原位，绝不丢弃。
 */
public final class Qty {
    private static final double PS_PER_NS = 1000.0d;
    private static final double PS_PER_US = 1_000_000.0d;

    public final String raw;
    public final String unit;
    public final Double valuePs;
    public final boolean convertible;
    public final String diagnostic;

    private Qty(String raw, String unit, Double valuePs, String diagnostic) {
        this.raw = raw;
        this.unit = unit;
        this.valuePs = valuePs;
        this.convertible = valuePs != null;
        this.diagnostic = diagnostic;
    }

    public static Qty parse(String raw) {
        Objects.requireNonNull(raw, "qty raw");
        String text = raw.trim();
        if (text.isEmpty()) {
            return new Qty(raw, "", null, "空的量值");
        }
        String[] parts = text.split("\\s+", 2);
        String number = parts[0];
        String unit = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
        double factor;
        switch (unit) {
            case "ps":
                factor = 1.0d;
                break;
            case "ns":
                factor = PS_PER_NS;
                break;
            case "us":
                factor = PS_PER_US;
                break;
            default:
                return new Qty(raw, unit, null,
                        "无法识别的时间单位 '" + unit + "'（支持 ps/ns/us），原始值保留在原位");
        }
        try {
            double value = Double.parseDouble(number) * factor;
            if (!Double.isFinite(value)) {
                return new Qty(raw, unit, null, "量值不是有限数: " + number);
            }
            return new Qty(raw, unit, value, null);
        } catch (NumberFormatException e) {
            return new Qty(raw, unit, null, "无法解析的数值 '" + number + "'");
        }
    }

    /** 派生量：规范化为 ps，raw 记录其规范化来源。 */
    public static Qty ofPs(double valuePs, String derivedRaw) {
        return new Qty(derivedRaw, "ps", valuePs, null);
    }

    @Override
    public String toString() {
        return raw;
    }
}
