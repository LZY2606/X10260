package com.fpgatiming.workbench.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A domain value carrying its raw representation and unit. The import layer
 * never throws away the original text; normalization to picoseconds only
 * happens in the derived layer. Values that cannot be converted stay in place
 * with a diagnostic instead of being dropped for the sake of drawing.
 */
public final class Quantity {

    private static final Pattern NUMBER = Pattern.compile("^([+-]?(?:\\d+\\.\\d+|\\.\\d+|\\d+)(?:[eE][+-]?\\d+)?)\\s*([A-Za-z°%/^0-9.]*)$");

    private static final Map<String, Double> PS_FACTORS = new LinkedHashMap<>();

    static {
        PS_FACTORS.put("ps", 1.0d);
        PS_FACTORS.put("ns", 1_000.0d);
        PS_FACTORS.put("us", 1_000_000.0d);
        PS_FACTORS.put("µs", 1_000_000.0d);
        PS_FACTORS.put("ms", 1_000_000_000.0d);
        PS_FACTORS.put("s", 1_000_000_000_000.0d);
        PS_FACTORS.put("deg", Double.NaN);
        PS_FACTORS.put("°", Double.NaN);
        PS_FACTORS.put("%", Double.NaN);
    }

    public String raw;
    public String unit;
    public Double value;
    public Double canonicalPs;
    public boolean convertible;
    public String diagnostic;
    public String basis;

    public static Quantity time(String raw, String defaultUnit) {
        Quantity quantity = new Quantity();
        quantity.raw = raw == null ? "" : raw.trim();
        quantity.basis = "time";
        Matcher matcher = NUMBER.matcher(quantity.raw);
        if (!matcher.matches()) {
            quantity.unit = defaultUnit;
            quantity.convertible = false;
            quantity.diagnostic = "UNPARSEABLE_NUMBER: '" + quantity.raw + "' kept at original position";
            return quantity;
        }
        quantity.value = Double.parseDouble(matcher.group(1));
        String parsedUnit = matcher.group(2).trim();
        quantity.unit = parsedUnit.isEmpty() ? defaultUnit : parsedUnit;
        Double factor = PS_FACTORS.get(quantity.unit);
        if (factor == null) {
            quantity.convertible = false;
            quantity.diagnostic = "UNKNOWN_TIME_UNIT: '" + quantity.unit + "' (raw representation retained)";
        } else if (Double.isNaN(factor)) {
            quantity.convertible = false;
            quantity.diagnostic = "NON_TIME_UNIT: '" + quantity.unit + "' needs an explicit conversion basis";
        } else {
            quantity.canonicalPs = quantity.value * factor;
            quantity.convertible = true;
        }
        return quantity;
    }

    public static Quantity phase(String raw, String defaultUnit, Double periodPs) {
        Quantity quantity = time(raw, defaultUnit);
        if (periodPs != null && quantity.value != null) {
            if ("deg".equals(quantity.unit) || "°".equals(quantity.unit)) {
                quantity.canonicalPs = periodPs * quantity.value / 360.0d;
                quantity.convertible = true;
                quantity.diagnostic = null;
                quantity.basis = "phase:deg->ps/period=" + periodPs + "ps";
            }
        }
        return quantity;
    }

    public static Quantity rawOnly(String raw, String diagnostic) {
        Quantity quantity = new Quantity();
        quantity.raw = raw == null ? "" : raw.trim();
        quantity.unit = "";
        quantity.convertible = false;
        quantity.diagnostic = diagnostic;
        quantity.basis = "unconverted";
        return quantity;
    }
}
