package com.naturaldrops.controller;

import com.naturaldrops.dto.request.CreateOrderRequest;
import com.naturaldrops.dto.request.UpdateOrderRequest;
import com.naturaldrops.dto.request.UpdateOrderBillRequest;
import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.OrderStatusHistory;
import com.naturaldrops.service.OrderService;
import com.naturaldrops.service.OrderPdfExportService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
// CORS is handled globally by CorsConfig - no need for controller-level annotation
public class OrderController {
    
    private final OrderService orderService;
    private final OrderPdfExportService orderPdfExportService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<Order>>> getAllOrders(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String status
    ) {
        List<Order> orders = orderService.getOrdersFiltered(status, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Order>> getOrderById(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping(value = "/{id}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportSingleOrderPdf(
            @PathVariable Long id,
            @RequestParam(required = false) String sellerName
    ) {
        Order order = orderService.getOrderById(id);
        byte[] pdf = orderPdfExportService.generateSingleOrderPdf(order, sellerName != null ? sellerName : "Seller");
        String date = java.time.LocalDate.now().toString();
        String filename = "order_" + id + "_" + date + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportOrdersPdf(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sellerName
    ) {
        List<Order> orders = orderService.getOrdersFiltered(status, fromDate, toDate);
        byte[] pdf = orderPdfExportService.generateMultiOrderPdf(orders, sellerName != null ? sellerName : "Seller");
        String date = java.time.LocalDate.now().toString();
        String filename = "orders_" + date + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
    
    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByBuyer(@PathVariable Long buyerId) {
        List<Order> orders = orderService.getOrdersByBuyerId(buyerId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    @GetMapping("/buyer/{buyerId}/status/{status}")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByBuyerAndStatus(
            @PathVariable Long buyerId,
            @PathVariable Order.OrderStatus status) {
        List<Order> orders = orderService.getOrdersByBuyerIdAndStatus(buyerId, status);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByStatus(@PathVariable Order.OrderStatus status) {
        List<Order> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<OrderStatusHistory>>> getOrderStatusHistory(@PathVariable Long id) {
        List<OrderStatusHistory> history = orderService.getOrderStatusHistory(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
    
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<Order>>> getOrdersByPeriod(@RequestParam String period) {
        List<Order> orders;
        switch (period.toLowerCase()) {
            case "today":
                orders = orderService.getTodayOrders();
                break;
            case "week":
                orders = orderService.getWeekOrders();
                break;
            case "month":
                orders = orderService.getMonthOrders();
                break;
            default:
                orders = orderService.getAllOrders();
        }
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<Order>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.ok(ApiResponse.success("Order placed successfully", order));
    }

    /**
     * Edit order before delivered (Pending/Confirmed only)
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Order>> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request
    ) {
        Order order = orderService.updateOrder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Order updated successfully", order));
    }
    
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Order>> updateOrderStatus(@PathVariable Long id, @RequestParam Order.OrderStatus status) {
        Order order = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Order status updated", order));
    }
    
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Order>> confirmOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            javax.servlet.http.HttpServletRequest request) {
        // TODO: Validate seller authorization
        // User currentUser = (User) request.getAttribute("currentUser");
        // if (currentUser == null || (currentUser.getRole() != User.UserRole.seller && currentUser.getRole() != User.UserRole.admin)) {
        //     throw new UnauthorizedException("Only sellers and admins can confirm orders");
        // }
        String confirmedBy = payload.getOrDefault("confirmedBy", "seller");
        Order order = orderService.confirmOrder(id, confirmedBy);
        return ResponseEntity.ok(ApiResponse.success("Order confirmed successfully", order));
    }
    
    @PutMapping("/{id}/process")
    public ResponseEntity<ApiResponse<Order>> markAsProcessing(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        String trackingNumber = (String) payload.get("trackingNumber");
        String deliveryPartner = (String) payload.get("deliveryPartner");
        String estimatedDeliveryStr = (String) payload.get("estimatedDelivery");
        String updatedBy = (String) payload.getOrDefault("updatedBy", "admin");
        
        LocalDateTime estimatedDelivery = estimatedDeliveryStr != null 
            ? LocalDateTime.parse(estimatedDeliveryStr) 
            : LocalDateTime.now().plusDays(3);
        
        Order order = orderService.markAsProcessing(id, trackingNumber, deliveryPartner, estimatedDelivery, updatedBy);
        return ResponseEntity.ok(ApiResponse.success("Order marked as processing", order));
    }
    
    @PutMapping("/{id}/on-the-way")
    public ResponseEntity<ApiResponse<Order>> setOnTheWay(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        Long deliveryTotalSeconds = payload.get("deliveryTotalSeconds") != null
            ? ((Number) payload.get("deliveryTotalSeconds")).longValue()
            : null;
        Integer deliveryTime = payload.get("deliveryTime") != null
            ? ((Number) payload.get("deliveryTime")).intValue()
            : null;
        String updatedBy = (String) payload.getOrDefault("updatedBy", "seller");
        
        // Backward compatibility: accept legacy minutes field if seconds not provided
        if ((deliveryTotalSeconds == null || deliveryTotalSeconds <= 0) && (deliveryTime != null && deliveryTime > 0)) {
            deliveryTotalSeconds = deliveryTime * 60L;
        }

        if (deliveryTotalSeconds == null || deliveryTotalSeconds <= 0) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Delivery time must be a positive number"));
        }
        
        Order order = orderService.setOnTheWay(id, deliveryTotalSeconds, updatedBy);
        return ResponseEntity.ok(ApiResponse.success("Order set to On The Way", order));
    }
    
    @PutMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<Order>> markAsDelivered(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        String deliveredBy = payload.getOrDefault("deliveredBy", "admin");
        Order order = orderService.markAsDelivered(id, deliveredBy);
        return ResponseEntity.ok(ApiResponse.success("Order marked as delivered", order));
    }
    
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            javax.servlet.http.HttpServletRequest request) {
        // TODO: Validate seller authorization
        // User currentUser = (User) request.getAttribute("currentUser");
        // if (currentUser == null || (currentUser.getRole() != User.UserRole.seller && currentUser.getRole() != User.UserRole.admin)) {
        //     throw new UnauthorizedException("Only sellers and admins can cancel orders");
        // }
        String canceledBy = payload.getOrDefault("canceledBy", "seller");
        String reason = payload.get("reason");
        Order order = orderService.cancelOrder(id, canceledBy, reason);
        return ResponseEntity.ok(ApiResponse.success("Order canceled successfully", order));
    }
    
    /**
     * Update order bill and payment status (Seller only)
     * Can only be updated for orders in "processing" (On The Way) status
     */
    @PutMapping("/{id}/bill")
    public ResponseEntity<ApiResponse<Order>> updateOrderBill(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderBillRequest request,
            javax.servlet.http.HttpServletRequest httpRequest) {
        // Get current user from request (set by JwtTokenFilter)
        // User currentUser = (User) httpRequest.getAttribute("currentUser");
        // if (currentUser == null || (currentUser.getRole() != User.UserRole.seller && currentUser.getRole() != User.UserRole.admin)) {
        //     throw new UnauthorizedException("Only sellers and admins can update order bills");
        // }
        
        Order order = orderService.updateOrderBill(id, request);
        return ResponseEntity.ok(ApiResponse.success("Order bill updated successfully", order));
    }
}

