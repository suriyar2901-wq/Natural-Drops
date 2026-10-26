package com.naturaldrops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "cashfree")
public class CashfreeProperties {

    /** Cashfree x-client-id */
    private String clientId = "";

    /** Cashfree x-client-secret — never log or expose */
    private String clientSecret = "";

    /** SANDBOX (default) or PRODUCTION */
    private String env = "SANDBOX";

    /** API version header */
    private String apiVersion = "2023-08-01";

    public boolean isSandbox() {
        return env == null || !"PRODUCTION".equalsIgnoreCase(env.trim());
    }

    public String baseUrl() {
        return isSandbox() ? "https://sandbox.cashfree.com/pg" : "https://api.cashfree.com/pg";
    }

    public String environmentLabel() {
        return isSandbox() ? "SANDBOX" : "PRODUCTION";
    }

    public void requireConfigured() {
        if (clientId == null || clientId.trim().isEmpty()
                || clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Cashfree is not configured. Set CASHFREE_CLIENT_ID and CASHFREE_CLIENT_SECRET.");
        }
    }
}
