package workbench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public final class Util {
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();
    public static final Gson PRETTY = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private Util() {}

    public static String sha12(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update(p.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0x1f);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.substring(0, 12);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** Deterministic id from kind and content parts. */
    public static String id(String prefix, String... parts) {
        return prefix + "-" + sha12(parts);
    }

    public static String join(List<String> xs) {
        return String.join(",", xs);
    }
}
