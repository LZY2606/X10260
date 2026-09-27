package workbench.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Hash {
    private Hash() {}

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
