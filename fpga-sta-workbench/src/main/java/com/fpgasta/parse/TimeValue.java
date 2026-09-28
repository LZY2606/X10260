package com.fpgasta.parse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fpgasta.model.Json;

/**
 * Parses a timing literal while keeping the original representation.
 *
 * Units are first-class data: a value without a unit cannot be plotted and is
 * kept at the source position together with a diagnostic instead of being
 * silently converted.
 */
public final class TimeValue {

    private TimeValue() {
    }

    public static ObjectNode parse(String raw) {
        ObjectNode node = Json.object();
        String trimmed = raw.trim();
        node.put("raw", raw);
        node.put("rawTrimmed", trimmed);
        if (trimmed.isEmpty()) {
            node.putNull("ns");
            node.put("convertible", false);
            node.put("note", "empty timing literal");
            return node;
        }
        String unit;
        String numberPart;
        if (trimmed.endsWith("ns")) {
            unit = "ns";
            numberPart = trimmed.substring(0, trimmed.length() - 2).trim();
        } else if (trimmed.endsWith("ps")) {
            unit = "ps";
            numberPart = trimmed.substring(0, trimmed.length() - 2).trim();
        } else {
            node.putNull("ns");
            node.put("convertible", false);
            node.put("unit", "");
            node.put("note", "missing or unsupported unit (accepted: ns, ps)");
            return node;
        }
        try {
            double value = Double.parseDouble(numberPart);
            double ns = unit.equals("ns") ? value : value / 1000.0d;
            node.put("value", value);
            node.put("unit", unit);
            node.put("ns", ns);
            node.put("convertible", true);
            node.put("basis", "1 ps = 0.001 ns");
            return node;
        } catch (NumberFormatException e) {
            node.putNull("ns");
            node.put("convertible", false);
            node.put("unit", unit);
            node.put("note", "numeric portion is not a number: '" + numberPart + "'");
            return node;
        }
    }
}
