package com.fpgatiming.workbench.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic stable identities. Everything that must survive export/import
 * is derived from content parts joined with a unit separator, never from a
 * random seed or a database row id.
 */
public final class Ids {

    private static final char SEP = '\u0001';

    private Ids() {
    }

    public static String stableId(String... parts) {
        return sha256(String.join(String.valueOf(SEP), parts));
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
