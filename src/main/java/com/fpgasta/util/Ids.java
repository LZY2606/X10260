package com.fpgasta.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic, content-addressed identity helpers. No randomness, no wall-clock. */
public final class Ids {
    private Ids() {}

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String shortHash(String s) {
        return sha256(s).substring(0, 16);
    }

    /** Canonical JSON serialization: maps sorted by key, arrays in order, no whitespace. */
    public static String canonical(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder sb) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String s) { sb.append(quote(s)); return; }
        if (o instanceof Boolean || o instanceof Integer || o instanceof Long) { sb.append(o); return; }
        if (o instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) sb.append((long) (double) d).append(".0");
            else sb.append(d);
            return;
        }
        if (o instanceof Map<?, ?> m) {
            TreeMap<String, Object> t = new TreeMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) t.put(String.valueOf(e.getKey()), e.getValue());
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : t.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(e.getKey())).append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (o instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(','); write(l.get(i), sb); }
            sb.append(']');
            return;
        }
        if (o.getClass().isArray()) {
            sb.append('[');
            int n = java.lang.reflect.Array.getLength(o);
            for (int i = 0; i < n; i++) { if (i > 0) sb.append(','); write(java.lang.reflect.Array.get(o, i), sb); }
            sb.append(']');
            return;
        }
        sb.append(quote(String.valueOf(o)));
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Deterministic id: prefix + short content hash. */
    public static String id(String prefix, Object content) {
        return prefix + "-" + shortHash(canonical(content));
    }

    public static List<String> sortedCopy(List<String> in) {
        List<String> c = new ArrayList<>(in);
        c.sort(Comparator.naturalOrder());
        return c;
    }
}
