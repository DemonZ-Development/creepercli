

package dev.demonzdevelopment.creepercli.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class TotpManager {
    private static final char[] B32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int[] B32_REVERSE = new int[128];

    static {
        for (int i = 0; i < 128; i++) B32_REVERSE[i] = -1;
        for (int i = 0; i < 32; i++) B32_REVERSE[B32_ALPHABET[i]] = i;
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long PERIOD_SECONDS = 30;

    public String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String otpauthUri(String user, String secret) {
        String issuer = "CreeperCLI";
        String label = URLEncoder.encode(issuer + ":" + user, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=6&period=" + PERIOD_SECONDS;
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{6}")) return false;
        long now = System.currentTimeMillis() / 1000;
        for (long window = -1; window <= 1; window++) {
            if (generateCode(secret, now + window * PERIOD_SECONDS).equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String generateCode(String secret, long timeSeconds) {
        try {
            byte[] key = decodeBase32(secret);
            long counter = timeSeconds / PERIOD_SECONDS;
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32_ALPHABET[(buffer >>> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(B32_ALPHABET[(buffer << (5 - bits)) & 0x1f]);
        }
        return sb.toString();
    }

    private static byte[] decodeBase32(String input) {
        String clean = input.toUpperCase().replaceAll("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c >= 128 || B32_REVERSE[c] == -1) {
                throw new IllegalArgumentException("Invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | B32_REVERSE[c];
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >>> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
