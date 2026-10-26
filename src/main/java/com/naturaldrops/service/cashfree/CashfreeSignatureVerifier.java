package com.naturaldrops.service.cashfree;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Official Cashfree PG webhook signature algorithm:
 * expected = Base64(HMAC-SHA256(x-webhook-timestamp + rawBody, clientSecret))
 */
public final class CashfreeSignatureVerifier {

    private CashfreeSignatureVerifier() {
    }

    public static String computeSignature(String timestamp, String rawBody, String clientSecret) {
        if (timestamp == null || rawBody == null || clientSecret == null) {
            throw new IllegalArgumentException("timestamp, rawBody, and clientSecret are required");
        }
        try {
            String data = timestamp + rawBody;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(keySpec);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute Cashfree webhook signature", ex);
        }
    }

    public static boolean isValid(String receivedSignature, String timestamp, String rawBody, String clientSecret) {
        if (receivedSignature == null || receivedSignature.trim().isEmpty()) {
            return false;
        }
        String expected = computeSignature(timestamp, rawBody, clientSecret);
        return constantTimeEquals(expected, receivedSignature.trim());
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
