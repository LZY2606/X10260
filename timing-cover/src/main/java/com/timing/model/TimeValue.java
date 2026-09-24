package com.timing.model;

import java.util.Locale;

/**
 * 领域量：导入时保留原始表示（rawText）与坐标基准（basis），
 * 仅派生层做规范化（normalizedNs）。无法转换时 convertible=false，
 * 原值留在原位置并附诊断，绝不丢弃。
 */
public record TimeValue(String rawText, String unit, Double normalizedNs, String basis,
                        boolean convertible, String diagnostic) {

    public static TimeValue unconvertible(String rawText, String basis, String diagnostic) {
        return new TimeValue(rawText, null, null, basis, false, diagnostic);
    }

    /** 解析带单位的时间量；仅做规范化，原始文本原样保留。 */
    public static TimeValue parse(String rawText, String basis) {
        if (rawText == null) {
            return unconvertible(null, basis, "空时间量");
        }
        String t = rawText.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("N/A") || t.equalsIgnoreCase("unconstrained")) {
            return unconvertible(rawText, basis, "报文未给出可转换数值: " + t);
        }
        String num;
        String unit;
            // 形如 10.000ns / 10 ns / 10
            int cut = t.length();
            while (cut > 0 && Character.isLetter(t.charAt(cut - 1))) {
                cut--;
            }
            num = t.substring(0, cut).trim();
            unit = t.substring(cut).trim().toLowerCase(Locale.ROOT);
        if (unit.isEmpty()) {
            unit = "ns"; // FPGA 时序报告默认基准单位
        }
        try {
            double v = Double.parseDouble(num);
            Double ns = switch (unit) {
                case "ns" -> v;
                case "ps" -> v / 1000.0;
                case "us" -> v * 1000.0;
                case "ms" -> v * 1_000_000.0;
                case "s" -> v * 1_000_000_000.0;
                default -> null;
            };
            if (ns == null) {
                return unconvertible(rawText, basis, "未知时间单位: " + unit);
            }
            return new TimeValue(rawText, unit, ns, basis, true, null);
        } catch (NumberFormatException e) {
            return unconvertible(rawText, basis, "无法解析为数值: " + num);
        }
    }

    public double requireNs() {
        if (!convertible || normalizedNs == null) {
            throw new IllegalStateException("不可转换的时间量: " + rawText + " (" + diagnostic + ")");
        }
        return normalizedNs;
    }
}
