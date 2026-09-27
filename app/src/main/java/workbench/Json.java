package workbench;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import java.util.Map;

public final class Json {
    private static final ObjectMapper M = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper COMPACT = new ObjectMapper();

    private Json() {}

    public static String write(Object value) {
        try {
            return COMPACT.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String writePretty(Object value) {
        try {
            return M.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Object> readMap(String json) {
        try {
            return COMPACT.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("bad json: " + json, e);
        }
    }

    public static List<Object> readList(String json) {
        try {
            return COMPACT.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("bad json: " + json, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> readStringList(String json) {
        try {
            return COMPACT.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("bad json: " + json, e);
        }
    }
}
