package tbench.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Deterministic content-addressed identity helpers (stable across replay). */
public final class Ids {
    private Ids() {
    }

    public static String sha1(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    md.update((byte) 0x1f);
                }
                md.update(parts[i] == null ? new byte[0] : parts[i].getBytes(StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    public static String shortId(String prefix, String... parts) {
        return prefix + "_" + sha1(parts).substring(0, 12);
    }
}
