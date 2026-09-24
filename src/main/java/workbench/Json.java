package workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {}

    public static String write(Object value) {
        try {
            return M.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("json write failed", e);
        }
    }

    public static <T> T read(String text, Class<T> type) {
        try {
            return M.readValue(text, type);
        } catch (Exception e) {
            throw new IllegalStateException("json read failed", e);
        }
    }

    public static JsonNode tree(String text) {
        try {
            return M.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("json parse failed", e);
        }
    }
}
