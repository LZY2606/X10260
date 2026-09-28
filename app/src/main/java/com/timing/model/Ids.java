package com.timing.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stable identity helpers. IDs are content-addressed where possible so that
 * export -> delete local DB -> import preserves identity, ordering and provenance.
 */
public final class Ids {

    private Ids() {
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String shortId(String kind, String content) {
        return kind + "_" + sha256Hex(content).substring(0, 16);
    }

    public static String projectId(String caseKey) {
        return shortId("proj", "project:" + caseKey);
    }

    public static String docId(String projectId, String type, String versionLabel, String rawText) {
        return shortId("doc", projectId + "|" + type + "|" + versionLabel + "|" + sha256Hex(rawText));
    }

    public static String eventId(long seq, String type, String payloadJson) {
        return "evt_" + String.format("%06d", seq) + "_"
                + sha256Hex(seq + "|" + type + "|" + payloadJson).substring(0, 12);
    }
}
