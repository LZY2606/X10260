package workbench.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {}

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

    public static String pretty(JsonNode n) {
        try { return M.writerWithDefaultPrettyPrinter().writeValueAsString(n); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
