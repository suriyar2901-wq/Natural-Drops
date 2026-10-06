package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.dto.response.SellerSubscriptionAccessResponse;
import com.naturaldrops.entity.CanEvent;
import com.naturaldrops.entity.LedgerEvent;
import com.naturaldrops.entity.ShopCustomer;
import com.naturaldrops.entity.SellerInboxMessage;
import com.naturaldrops.entity.ShopProfile;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.SellerAdminService;
import com.naturaldrops.service.ShopService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final SellerAdminService sellerAdminService;

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<List<ShopCustomer>>> listCustomers(HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.listCustomers(user.getId())));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<ShopCustomer>> getCustomer(@PathVariable Long id, HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.getCustomer(user.getId(), id)));
    }

    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<ShopCustomer>> createCustomer(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Customer created", shopService.createCustomer(user.getId(), body)));
    }

    @PutMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<ShopCustomer>> updateCustomer(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Customer updated", shopService.updateCustomer(user.getId(), id, body)));
    }

    @GetMapping("/customers/{id}/ledger")
    public ResponseEntity<ApiResponse<List<LedgerEvent>>> ledger(@PathVariable Long id, HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.listLedger(user.getId(), id)));
    }

    @PostMapping("/customers/{id}/payments")
    public ResponseEntity<ApiResponse<ShopCustomer>> recordPayment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Payment recorded", shopService.recordPayment(user.getId(), id, body, user.getUsername())));
    }

    @GetMapping("/customers/{id}/cans")
    public ResponseEntity<ApiResponse<List<CanEvent>>> canEvents(@PathVariable Long id, HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.listCanEvents(user.getId(), id)));
    }

    @PostMapping("/customers/{id}/cans")
    public ResponseEntity<ApiResponse<ShopCustomer>> collectCans(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        Integer qty = body == null || body.get("quantity") == null ? null : Integer.valueOf(String.valueOf(body.get("quantity")));
        return ResponseEntity.ok(ApiResponse.success("Empty cans collected", shopService.collectCans(user.getId(), id, qty)));
    }

    @PostMapping("/phone-orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> phoneOrder(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Phone order created", shopService.createPhoneOrder(user.getId(), body, user.getUsername())));
    }

    @GetMapping("/can-ledger")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> canLedger(HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.canLedger(user.getId())));
    }

    @GetMapping("/company")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myCompany(HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.companySummary(user)));
    }

    @GetMapping("/buyers")
    public ResponseEntity<ApiResponse<List<User>>> myBuyers(HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.listLinkedBuyers(user)));
    }

    @PostMapping("/buyers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBuyer(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireSeller(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Buyer account created",
                shopService.createLinkedBuyer(user, body)
        ));
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<SellerInboxMessage>>> inbox(HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.listInbox(user)));
    }

    @PostMapping("/inbox/{id}/read")
    public ResponseEntity<ApiResponse<Object>> markInboxRead(@PathVariable Long id, HttpServletRequest request) {
        User user = requireStaff(request);
        shopService.markInboxRead(user, id);
        return ResponseEntity.ok(ApiResponse.success("Message marked as read", null));
    }

    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<SellerSubscriptionAccessResponse>> getSubscription(HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(sellerAdminService.getAccessForUser(user)));
    }

    @PostMapping("/subscription")
    public ResponseEntity<ApiResponse<SellerSubscriptionAccessResponse>> subscribe(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        User user = requireSeller(request);
        String plan = body == null || body.get("plan") == null ? "MONTHLY" : String.valueOf(body.get("plan"));
        String method = body == null || body.get("method") == null ? "UPI" : String.valueOf(body.get("method"));
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription activated",
                sellerAdminService.subscribeForUser(user, plan, method)
        ));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ShopProfile>> getProfile(HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success(shopService.getProfile(user.getId())));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ShopProfile>> saveProfile(
            @RequestBody ShopProfile body,
            HttpServletRequest request) {
        User user = requireStaff(request);
        return ResponseEntity.ok(ApiResponse.success("Shop profile saved", shopService.saveProfile(user.getId(), body)));
    }

    private User requireStaff(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        if (currentUser.getRole() != User.UserRole.admin && currentUser.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Access denied. Only sellers can access shop resources.");
        }
        return currentUser;
    }

    private User requireSeller(HttpServletRequest request) {
        User currentUser = requireStaff(request);
        if (currentUser.getRole() != User.UserRole.seller) {
            throw new UnauthorizedException("Only seller accounts can subscribe from this screen.");
        }
        return currentUser;
    }
}
