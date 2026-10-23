package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Returns Dozzle log-viewer URL for admin UI (frontend + backend containers).
 * Auth: same as other /api/* routes (JWT required).
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class DozzleLogsController {

    private final SettingsService settingsService;

    @Value("${app.dozzle.url:http://localhost:8082}")
    private String defaultDozzleUrl;

    @GetMapping("/dozzle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDozzleConfig() {
        Map<String, String> settings = settingsService.getAllSettings();
        String url = firstFilled(settings.get("dozzleUrl"), defaultDozzleUrl);

        Map<String, Object> body = new HashMap<>();
        body.put("dozzleUrl", url);
        body.put("enabled", url != null && !url.isBlank());
        body.put("hint", "Open Dozzle to watch backend and frontend container logs live. Filter by container name natural-drops-backend / natural-drops-frontend.");
        body.put("services", Map.of(
                "backend", "natural-drops-backend",
                "frontend", "natural-drops-frontend",
                "dozzle", "natural-drops-dozzle"
        ));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    private static String firstFilled(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
