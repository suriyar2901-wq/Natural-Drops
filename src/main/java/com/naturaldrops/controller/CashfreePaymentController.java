package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.CashfreePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/cashfree")
@RequiredArgsConstructor
public class CashfreePaymentController {

    private final CashfreePaymentService cashfreePaymentService;

    /**
     * Create a Cashfree order for an unpaid Natural Drops bill.
     * Body: { "orderId": &lt;ndOrderId&gt;, "amount"?: "&lt;optional&gt;" }.
     * Amount defaults to full unpaid balance; when sent, server caps it at the live balance.
     */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrder(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User buyer = requireBuyer(request);
        Long orderId = body == null || body.get("orderId") == null ? null : toLong(body.get("orderId"));
        BigDecimal amount = body == null ? null : toAmount(body.get("amount"));
        return ResponseEntity.ok(ApiResponse.success(
                "Cashfree order created",
                cashfreePaymentService.createOrderForBuyer(buyer, orderId, amount)));
    }

    /**
     * Verify payment status with Cashfree and apply if PAID (idempotent).
     */
    @GetMapping("/orders/{cfOrderId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @PathVariable String cfOrderId,
            HttpServletRequest request) {
        User buyer = requireBuyer(request);
        return ResponseEntity.ok(ApiResponse.success(
                cashfreePaymentService.verifyAndRefreshStatus(buyer, cfOrderId)));
    }

    /**
     * Create a Cashfree order for seller platform subscription.
     * Body: { "plan": "MONTHLY" | "YEARLY" }.
     */
    @PostMapping("/subscription/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSubscriptionOrder(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User seller = requireSeller(request);
        String plan = body == null || body.get("plan") == null ? "MONTHLY" : String.valueOf(body.get("plan"));
        return ResponseEntity.ok(ApiResponse.success(
                "Cashfree subscription order created",
                cashfreePaymentService.createOrderForSellerSubscription(seller, plan)));
    }

    /**
     * Verify subscription Cashfree payment and activate/renew when PAID (idempotent).
     */
    @GetMapping("/subscription/orders/{cfOrderId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> subscriptionStatus(
            @PathVariable String cfOrderId,
            HttpServletRequest request) {
        User seller = requireSeller(request);
        return ResponseEntity.ok(ApiResponse.success(
                cashfreePaymentService.verifyAndRefreshSubscriptionStatus(seller, cfOrderId)));
    }

    /**
     * Public webhook — JWT skipped via JwtTokenFilter PUBLIC_ENDPOINTS.
     * Signature verified on the original raw body before any processing.
     */
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Map<String, Object>>> webhook(
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp,
            @RequestBody String rawBody) {
        return ResponseEntity.ok(ApiResponse.success(
                cashfreePaymentService.handleWebhook(signature, timestamp, rawBody)));
    }

    private User requireBuyer(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        if (user.getRole() != User.UserRole.buyer) {
            throw new UnauthorizedException("Only buyers can use Cashfree bill payment");
        }
        return user;
    }

    private User requireSeller(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        if (user.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Only sellers can use Cashfree subscription payment");
        }
        return user;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal toAmount(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (Exception ex) {
            throw new BadRequestException("Enter a valid amount");
        }
    }
}
