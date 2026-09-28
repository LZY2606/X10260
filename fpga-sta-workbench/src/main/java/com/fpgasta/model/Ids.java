package com.fpgasta.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stable, content-addressed identity helpers.
 *
 * Every identity in the workbench is derived from stable content rather than a
 * random value, so exporting the database, deleting it and importing again
 * preserves exactly the same ids.
 */
public final class Ids {

    private Ids() {
    }

    public static String sha8(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String of(String prefix, String content) {
        return prefix + "_" + sha8(content);
    }

    public static String valueId(String subject, String slot, String raw) {
        return "v_" + subject + "." + slot + "#" + sha8(subject + "|" + slot + "|" + raw);
    }

    public static String candidateId(String subject, String candidate, String basis) {
        return "cand_" + sha8(subject + "|" + candidate + "|" + basis);
    }
}
