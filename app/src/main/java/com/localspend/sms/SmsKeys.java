package com.localspend.sms;

import com.localspend.util.Dates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fingerprint of an SMS used to avoid importing it twice (a SHA-256 hash of sender, day and
 * text). It is a separate column from the stored message so de-duplication keeps working even if
 * the user clears stored messages. The day (not the exact time) is part of the key because the live receiver and
 * the inbox scan can disagree by a few seconds about when a message arrived.
 */
public final class SmsKeys {

    private SmsKeys() {
    }

    public static String keyFor(String sender, String body, long timestampMillis) {
        String raw = (sender == null ? "" : sender) + "|" + Dates.isoDay(timestampMillis)
                + "|" + (body == null ? "" : body);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
