package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
// CORS is handled globally by CorsConfig - no need for controller-level annotation
public class SettingsController {
    
    private final SettingsService settingsService;

    private static final String CUSTOMER_CONTACT_KEY = "businessPhone";
    private static final String CUSTOMER_SUPPORT_EMAIL_KEY = "customerSupportEmail";
    private static final Pattern TEN_DIGITS = Pattern.compile("^[0-9]{10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getAllSettings() {
        Map<String, String> settings = settingsService.getAllSettings();
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @GetMapping("/customer-contact-number")
    public ResponseEntity<ApiResponse<String>> getCustomerContactNumber() {
        String value = settingsService.getSetting(CUSTOMER_CONTACT_KEY);
        return ResponseEntity.ok(ApiResponse.success(value != null ? value : ""));
    }

    @PutMapping("/customer-contact-number")
    public ResponseEntity<ApiResponse<Object>> updateCustomerContactNumber(@RequestBody Map<String, String> payload) {
        String raw = payload != null ? payload.getOrDefault("phone", payload.getOrDefault("value", payload.getOrDefault("businessPhone", ""))) : "";
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");

        if (!TEN_DIGITS.matcher(digits).matches()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Customer contact number must be exactly 10 digits"));
        }

        settingsService.updateSetting(CUSTOMER_CONTACT_KEY, digits);
        return ResponseEntity.ok(ApiResponse.success("Customer contact number updated successfully", null));
    }

    @GetMapping("/customer-support-email")
    public ResponseEntity<ApiResponse<String>> getCustomerSupportEmail() {
        String value = settingsService.getSetting(CUSTOMER_SUPPORT_EMAIL_KEY);
        return ResponseEntity.ok(ApiResponse.success(value != null ? value : ""));
    }

    @PutMapping("/customer-support-email")
    public ResponseEntity<ApiResponse<Object>> updateCustomerSupportEmail(@RequestBody Map<String, String> payload) {
        String email = payload != null ? payload.getOrDefault("email", payload.getOrDefault("value", "")) : "";
        email = email != null ? email.trim() : "";

        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Customer support email is required"));
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid email format"));
        }

        settingsService.updateSetting(CUSTOMER_SUPPORT_EMAIL_KEY, email);
        return ResponseEntity.ok(ApiResponse.success("Customer support email updated successfully", null));
    }
    
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<String>> getSetting(@PathVariable String key) {
        String value = settingsService.getSetting(key);
        return ResponseEntity.ok(ApiResponse.success(value));
    }
    
    @PutMapping
    public ResponseEntity<ApiResponse<Object>> updateSettings(@RequestBody Map<String, String> settings) {
        settingsService.updateSettings(settings);
        return ResponseEntity.ok(ApiResponse.success("Settings updated successfully", null));
    }
    
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<Object>> updateSetting(@PathVariable String key, @RequestBody String value) {
        settingsService.updateSetting(key, value);
        return ResponseEntity.ok(ApiResponse.success("Setting updated successfully", null));
    }
}

