package com.naturaldrops.service.cashfree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naturaldrops.config.CashfreeProperties;
import com.naturaldrops.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin Cashfree PG HTTP client (create/fetch order). Credentials stay server-side.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CashfreeApiClient {

    private final CashfreeProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public JsonNode createOrder(
            String cfOrderId,
            BigDecimal amount,
            String customerId,
            String customerPhone,
            String customerEmail,
            String customerName) {
        properties.requireConfigured();
        Map<String, Object> customer = new HashMap<String, Object>();
        customer.put("customer_id", customerId);
        customer.put("customer_phone", sanitizePhone(customerPhone));
        if (customerEmail != null && !customerEmail.trim().isEmpty()) {
            customer.put("customer_email", customerEmail.trim());
        }
        if (customerName != null && !customerName.trim().isEmpty()) {
            customer.put("customer_name", customerName.trim());
        }

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("order_id", cfOrderId);
        body.put("order_amount", amount);
        body.put("order_currency", "INR");
        body.put("customer_details", customer);

        return post("/orders", body);
    }

    public JsonNode fetchOrder(String cfOrderId) {
        properties.requireConfigured();
        return get("/orders/" + cfOrderId);
    }

    private JsonNode post(String path, Object body) {
        try {
            HttpEntity<Object> entity = new HttpEntity<Object>(body, headers());
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.baseUrl() + path,
                    HttpMethod.POST,
                    entity,
                    String.class);
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.warn("Cashfree create/fetch failed: status={} body={}",
                    ex.getStatusCode(), truncate(ex.getResponseBodyAsString()));
            throw new BadRequestException(cashfreeErrorMessage(ex.getResponseBodyAsString(), "Cashfree request failed"));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cashfree API error", ex);
            throw new BadRequestException("Could not reach Cashfree. Try again later.");
        }
    }

    private JsonNode get(String path) {
        try {
            HttpEntity<Void> entity = new HttpEntity<Void>(headers());
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.baseUrl() + path,
                    HttpMethod.GET,
                    entity,
                    String.class);
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.warn("Cashfree fetch failed: status={} body={}",
                    ex.getStatusCode(), truncate(ex.getResponseBodyAsString()));
            throw new BadRequestException(cashfreeErrorMessage(ex.getResponseBodyAsString(), "Cashfree status check failed"));
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Cashfree API error", ex);
            throw new BadRequestException("Could not reach Cashfree. Try again later.");
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("x-client-id", properties.getClientId().trim());
        headers.set("x-client-secret", properties.getClientSecret().trim());
        headers.set("x-api-version", properties.getApiVersion());
        return headers;
    }

    private String sanitizePhone(String phone) {
        if (phone == null) {
            return "9999999999";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            return digits.substring(digits.length() - 10);
        }
        if (digits.length() > 0) {
            return digits;
        }
        return "9999999999";
    }

    private String cashfreeErrorMessage(String body, String fallback) {
        try {
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
            if (node.has("message") && node.get("message").isObject() && node.get("message").has("message")) {
                return node.get("message").get("message").asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return fallback;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 400 ? value.substring(0, 400) + "…" : value;
    }
}
