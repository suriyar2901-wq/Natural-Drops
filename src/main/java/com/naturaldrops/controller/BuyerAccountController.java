package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.ShopCustomer;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.ShopService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/buyer-account")
@RequiredArgsConstructor
public class BuyerAccountController {

    private final ShopService shopService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(HttpServletRequest request) {
        User user = requireBuyer(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.buyerSummary(user.getId(), user.getPhone())));
    }

    @PostMapping("/payments")
    public ResponseEntity<ApiResponse<ShopCustomer>> claimPayment(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireBuyer(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Payment recorded",
                shopService.buyerClaimPayment(user.getId(), user.getPhone(), body)
        ));
    }

    private User requireBuyer(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        if (currentUser.getRole() != User.UserRole.buyer) {
            throw new UnauthorizedException("Access denied. Only buyers can access this resource.");
        }
        return currentUser;
    }
}
