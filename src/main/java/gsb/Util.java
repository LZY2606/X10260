package gsb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared helpers: canonical JSON, fingerprints, unit-aware quantity parsing. */
public final class Util {
    public static final ObjectMapper M = new ObjectMapper();

    private Util() {}

    public static ObjectNode obj() { return M.createObjectNode(); }
    public static ArrayNode arr() { return M.createArrayNode(); }

    public static JsonNode parse(String s) {
        try { return M.readTree(s); }
        catch (Exception e) { throw new IllegalArgumentException("invalid JSON: " + e.getMessage(), e); }
    }

    public static String write(JsonNode n) {
        try { return M.writeValueAsString(n); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Canonical JSON: object keys sorted recursively, so fingerprints are stable. */
    public static String canonical(JsonNode n) {
        StringBuilder sb = new StringBuilder();
        canonicalInto(n, sb);
        return sb.toString();
    }

    private static void canonicalInto(JsonNode n, StringBuilder sb) {
        if (n == null || n.isNull()) { sb.append("null"); return; }
        if (n.isObject()) {
            sb.append('{');
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            n.fields().forEachRemaining(e -> sorted.put(e.getKey(), e.getValue()));
            boolean first = true;
            for (Map.Entry<String, JsonNode> e : sorted.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append('"').append(':');
                canonicalInto(e.getValue(), sb);
            }
            sb.append('}');
        } else if (n.isArray()) {
            sb.append('[');
            for (int i = 0; i < n.size(); i++) {
                if (i > 0) sb.append(',');
                canonicalInto(n.get(i), sb);
            }
            sb.append(']');
        } else {
            sb.append(n.toString());
        }
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** A domain quantity: raw representation preserved; value+unit only after normalization. */
    public static final class Qty {
        public final String raw;
        public final Double value;      // null when not convertible
        public final String unit;       // null when absent
        public final String diagnostic; // null when clean
        public Qty(String raw, Double value, String unit, String diagnostic) {
            this.raw = raw; this.value = value; this.unit = unit; this.diagnostic = diagnostic;
        }
        public boolean ok() { return value != null && diagnostic == null; }
        public ObjectNode toJson() {
            ObjectNode o = obj();
            o.put("raw", raw);
            if (value != null) o.put("value", value); else o.putNull("value");
            if (unit != null) o.put("unit", unit); else o.putNull("unit");
            if (diagnostic != null) o.put("diagnostic", diagnostic); else o.putNull("diagnostic");
            return o;
        }
    }

    private static final Pattern QTY = Pattern.compile("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*(ns|ps|deg)?\\s*$");

    /**
     * Parse a quantity like "10ns" / "90deg". Values that cannot be converted keep
     * their raw representation and carry a diagnostic instead of being dropped.
     */
    public static Qty parseQty(JsonNode node, String expectedUnit) {
        if (node == null || node.isNull())
            return new Qty(null, null, null, "missing_value");
        String raw = node.isTextual() ? node.asText() : node.toString();
        Matcher m = QTY.matcher(raw);
        if (!m.matches())
            return new Qty(raw, null, null, "unparseable_quantity");
        double v = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        if (unit == null)
            return new Qty(raw, null, null, "unit_missing_expected_" + expectedUnit);
        if (!unit.equals(expectedUnit))
            return new Qty(raw, null, unit, "unit_mismatch_expected_" + expectedUnit);
        return new Qty(raw, v, unit, null);
    }

    public static List<String> toStringList(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) n.forEach(x -> out.add(x.asText()));
        return out;
    }

    public static ArrayNode toJsonArray(List<String> items) {
        ArrayNode a = arr();
        items.forEach(a::add);
        return a;
    }
}
