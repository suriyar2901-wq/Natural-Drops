package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.PaymentClaim;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.PaymentClaimService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentClaimController {

    private final PaymentClaimService paymentClaimService;

    @GetMapping("/platform")
    public ResponseEntity<ApiResponse<Map<String, String>>> platformPayment(HttpServletRequest request) {
        requireUser(request);
        return ResponseEntity.ok(ApiResponse.success(paymentClaimService.platformPaymentDetails()));
    }

    @PutMapping("/platform")
    public ResponseEntity<ApiResponse<Map<String, String>>> savePlatformPayment(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Platform payment details saved",
                paymentClaimService.savePlatformPaymentDetails(body)));
    }

    @PostMapping("/subscription/claim")
    public ResponseEntity<ApiResponse<PaymentClaim>> claimSubscription(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User seller = requireSeller(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Payment sent for admin confirmation",
                paymentClaimService.claimSubscription(seller, body)));
    }

    @GetMapping("/subscription/mine")
    public ResponseEntity<ApiResponse<List<PaymentClaim>>> mySubscriptionClaims(HttpServletRequest request) {
        User seller = requireSeller(request);
        return ResponseEntity.ok(ApiResponse.success(paymentClaimService.mySubscriptionClaims(seller.getId())));
    }

    @GetMapping("/subscription/pending")
    public ResponseEntity<ApiResponse<List<PaymentClaim>>> pendingSubscriptions(HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(paymentClaimService.pendingSubscriptions()));
    }

    @PostMapping("/subscription/{id}/confirm")
    public ResponseEntity<ApiResponse<PaymentClaim>> confirmSubscription(
            @PathVariable Long id,
            HttpServletRequest request) {
        User admin = requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription payment confirmed",
                paymentClaimService.confirmSubscription(id, admin)));
    }

    @PostMapping("/subscription/{id}/reject")
    public ResponseEntity<ApiResponse<PaymentClaim>> rejectSubscription(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        User admin = requireAdmin(request);
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription payment rejected",
                paymentClaimService.rejectSubscription(id, admin, reason)));
    }

    @PostMapping("/order/claim")
    public ResponseEntity<ApiResponse<PaymentClaim>> claimOrder(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User buyer = requireBuyer(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Payment sent for seller confirmation",
                paymentClaimService.claimOrderBill(buyer, body)));
    }

    @GetMapping("/order/mine")
    public ResponseEntity<ApiResponse<List<PaymentClaim>>> myOrderClaims(HttpServletRequest request) {
        User buyer = requireBuyer(request);
        return ResponseEntity.ok(ApiResponse.success(paymentClaimService.myOrderClaims(buyer.getId())));
    }

    @GetMapping("/order/pending")
    public ResponseEntity<ApiResponse<List<PaymentClaim>>> pendingOrderBills(HttpServletRequest request) {
        User seller = requireSeller(request);
        return ResponseEntity.ok(ApiResponse.success(paymentClaimService.pendingOrderBillsForSeller(seller.getId())));
    }

    @PostMapping("/order/{id}/confirm")
    public ResponseEntity<ApiResponse<PaymentClaim>> confirmOrder(
            @PathVariable Long id,
            HttpServletRequest request) {
        User seller = requireSeller(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Bill payment confirmed",
                paymentClaimService.confirmOrderBill(id, seller)));
    }

    @PostMapping("/order/{id}/reject")
    public ResponseEntity<ApiResponse<PaymentClaim>> rejectOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        User seller = requireSeller(request);
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Bill payment rejected",
                paymentClaimService.rejectOrderBill(id, seller, reason)));
    }

    private User requireUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return user;
    }

    private User requireAdmin(HttpServletRequest request) {
        User user = requireUser(request);
        if (user.getRole() != User.UserRole.admin) {
            throw new UnauthorizedException("Only admin can manage platform payments");
        }
        return user;
    }

    private User requireSeller(HttpServletRequest request) {
        User user = requireUser(request);
        if (user.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Only sellers can access this resource");
        }
        return user;
    }

    private User requireBuyer(HttpServletRequest request) {
        User user = requireUser(request);
        if (user.getRole() != User.UserRole.buyer) {
            throw new UnauthorizedException("Only buyers can access this resource");
        }
        return user;
    }
}
