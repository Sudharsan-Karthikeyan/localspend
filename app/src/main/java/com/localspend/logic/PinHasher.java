package com.localspend.logic;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Stores a PIN as a salted PBKDF2 hash, never as the PIN itself. */
public final class PinHasher {

    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 8;
    private static final int ITERATIONS = 20_000;
    private static final int KEY_BITS = 256;

    private PinHasher() {
    }

    /** A PIN is 4 to 8 digits. */
    public static boolean isValidPin(String pin) {
        return pin != null && pin.matches("[0-9]{" + MIN_LENGTH + "," + MAX_LENGTH + "}");
    }

    public static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return toHex(salt);
    }

    public static String hash(String pin, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), fromHex(saltHex), ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return toHex(key);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        }
    }

    public static boolean matches(String pin, String saltHex, String expectedHash) {
        if (pin == null || saltHex == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(pin, saltHex).getBytes(), expectedHash.getBytes());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
