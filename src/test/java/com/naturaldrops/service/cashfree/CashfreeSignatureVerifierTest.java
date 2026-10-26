package com.naturaldrops.service.cashfree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashfreeSignatureVerifierTest {

    @Test
    void acceptsValidSignature() {
        String secret = "test_client_secret";
        String timestamp = "1617695238078";
        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\",\"data\":{\"order\":{\"order_id\":\"ND1\"}}}";
        String signature = CashfreeSignatureVerifier.computeSignature(timestamp, body, secret);
        assertTrue(CashfreeSignatureVerifier.isValid(signature, timestamp, body, secret));
    }

    @Test
    void rejectsTamperedBody() {
        String secret = "test_client_secret";
        String timestamp = "1617695238078";
        String body = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";
        String signature = CashfreeSignatureVerifier.computeSignature(timestamp, body, secret);
        assertFalse(CashfreeSignatureVerifier.isValid(signature, timestamp, body + " ", secret));
    }

    @Test
    void rejectsMissingSignature() {
        assertFalse(CashfreeSignatureVerifier.isValid(null, "1", "{}", "secret"));
        assertFalse(CashfreeSignatureVerifier.isValid("", "1", "{}", "secret"));
    }
}
