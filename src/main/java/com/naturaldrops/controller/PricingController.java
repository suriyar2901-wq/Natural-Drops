package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.PricingService;
import com.naturaldrops.service.SettingsService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;
    private final SettingsService settingsService;

    @GetMapping("/fees")
    public ResponseEntity<ApiResponse<Map<String, String>>> fees(HttpServletRequest request) {
        requireUser(request);
        return ResponseEntity.ok(ApiResponse.success(settingsService.feeSettings()));
    }

    @GetMapping("/quote")
    public ResponseEntity<ApiResponse<Map<String, Object>>> quote(
            @RequestParam(required = false) BigDecimal subtotal,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            HttpServletRequest request) {
        User user = requireUser(request);
        Map<String, Object> result;
        if (user.getRole() == User.UserRole.buyer) {
            result = pricingService.quoteForBuyer(user.getId(), subtotal, latitude, longitude);
        } else {
            result = pricingService.quote(subtotal, user.getId(), latitude, longitude);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private User requireUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return user;
    }
}
