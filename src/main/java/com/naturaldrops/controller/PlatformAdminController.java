package com.naturaldrops.controller;

import com.naturaldrops.dto.request.CreateSellerRequest;
import com.naturaldrops.dto.request.DeactivateSellerRequest;
import com.naturaldrops.dto.request.RecordSellerPaymentRequest;
import com.naturaldrops.dto.request.RenewSubscriptionRequest;
import com.naturaldrops.dto.request.UpdateSellerRequest;
import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.dto.response.PlatformDashboardResponse;
import com.naturaldrops.dto.response.SellerAdminResponse;
import com.naturaldrops.dto.response.SellerPaymentResponse;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.SellerAdminService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final SellerAdminService sellerAdminService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<PlatformDashboardResponse>> dashboard(
            @RequestParam(required = false) String period,
            HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(sellerAdminService.getDashboard(period)));
    }

    @GetMapping("/sellers")
    public ResponseEntity<ApiResponse<List<SellerAdminResponse>>> listSellers(HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(sellerAdminService.listSellers()));
    }

    @GetMapping("/sellers/{id}")
    public ResponseEntity<ApiResponse<SellerAdminResponse>> getSeller(@PathVariable Long id, HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(sellerAdminService.getSeller(id)));
    }

    @PostMapping("/sellers")
    public ResponseEntity<ApiResponse<SellerAdminResponse>> createSeller(
            @RequestBody CreateSellerRequest body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Seller created", sellerAdminService.createSeller(body, user.getUsername())));
    }

    @PutMapping("/sellers/{id}")
    public ResponseEntity<ApiResponse<SellerAdminResponse>> updateSeller(
            @PathVariable Long id,
            @RequestBody UpdateSellerRequest body,
            HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Seller updated", sellerAdminService.updateSeller(id, body)));
    }

    @PostMapping("/sellers/{id}/deactivate")
    public ResponseEntity<ApiResponse<SellerAdminResponse>> deactivateSeller(
            @PathVariable Long id,
            @RequestBody DeactivateSellerRequest body,
            HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Seller deactivated", sellerAdminService.deactivateSeller(id, body)));
    }

    @PostMapping("/sellers/{id}/reactivate")
    public ResponseEntity<ApiResponse<SellerAdminResponse>> reactivateSeller(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        requireStaff(request);
        String note = body != null ? body.get("adminNote") : null;
        return ResponseEntity.ok(ApiResponse.success("Seller reactivated", sellerAdminService.reactivateSeller(id, note)));
    }

    @PostMapping("/sellers/{id}/payments")
    public ResponseEntity<ApiResponse<SellerAdminResponse>> recordPayment(
            @PathVariable Long id,
            @RequestBody RecordSellerPaymentRequest body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Payment recorded and seller activated",
                sellerAdminService.activateWithPayment(id, body, user.getUsername())));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<SellerAdminResponse>>> listSubscriptions(HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(sellerAdminService.listSubscriptions()));
    }

    @PostMapping("/subscriptions/{sellerId}/renew")
    public ResponseEntity<ApiResponse<SellerAdminResponse>> renew(
            @PathVariable Long sellerId,
            @RequestBody RenewSubscriptionRequest body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription renewed",
                sellerAdminService.renewSubscription(sellerId, body, user.getUsername())));
    }

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<List<SellerPaymentResponse>>> listPayments(HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(sellerAdminService.listPayments()));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<ApiResponse<SellerPaymentResponse>> getPayment(@PathVariable Long id, HttpServletRequest request) {
        requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(sellerAdminService.getPayment(id)));
    }

    private User requireStaff(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        if (currentUser.getRole() != User.UserRole.admin && currentUser.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Access denied. Only administrators can access this resource.");
        }
        return currentUser;
    }
}
