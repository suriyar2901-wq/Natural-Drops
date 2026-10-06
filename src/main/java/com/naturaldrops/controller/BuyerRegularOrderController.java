package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.BuyerRegularPlan;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.UnauthorizedException;
import com.naturaldrops.service.BuyerRegularOrderScheduler;
import com.naturaldrops.service.BuyerRegularOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/buyer/regular-order")
public class BuyerRegularOrderController {

    private final BuyerRegularOrderService buyerRegularOrderService;
    private final BuyerRegularOrderScheduler buyerRegularOrderScheduler;

    public BuyerRegularOrderController(
            BuyerRegularOrderService buyerRegularOrderService,
            BuyerRegularOrderScheduler buyerRegularOrderScheduler
    ) {
        this.buyerRegularOrderService = buyerRegularOrderService;
        this.buyerRegularOrderScheduler = buyerRegularOrderScheduler;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BuyerRegularPlan>> mine(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(buyerRegularOrderService.mine(currentUser(request))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<BuyerRegularPlan>> save(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        BuyerRegularPlan plan = buyerRegularOrderService.save(currentUser(request), body);
        buyerRegularOrderScheduler.placeIfTomorrow(plan.getBuyerId());
        return ResponseEntity.ok(ApiResponse.success("Regular order saved", plan));
    }

    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<BuyerRegularPlan>> pause(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Regular order paused",
                buyerRegularOrderService.setPaused(currentUser(request), true)
        ));
    }

    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<BuyerRegularPlan>> resume(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Regular order resumed",
                buyerRegularOrderService.setPaused(currentUser(request), false)
        ));
    }

    private User currentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            throw new UnauthorizedException("Please login again");
        }
        return user;
    }
}
