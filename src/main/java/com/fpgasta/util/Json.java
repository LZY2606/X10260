package com.fpgasta.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();
    static {
        M.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private Json() {}

    public static String write(Object o) {
        try { return M.writeValueAsString(o); } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static String writePretty(Object o) {
        try { return M.writerWithDefaultPrettyPrinter().writeValueAsString(o); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String s) {
        try { return M.readValue(s, Map.class); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings("unchecked")
    public static <T> T read(String s, Class<T> t) {
        try { return M.readValue(s, t); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
