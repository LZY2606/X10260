package fpgasta.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable, content-derived identity helpers. Import/export preserves them byte-for-byte. */
public final class Ids {
    private Ids() {}

    public static String hash12(String kind, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((kind + "\u0000" + content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(kind).append('_');
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
