package com.naturaldrops.controller;

import com.naturaldrops.dto.response.ApiResponse;
import com.naturaldrops.entity.BuyerNotification;
import com.naturaldrops.entity.Notification;
import com.naturaldrops.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
// CORS is handled globally by CorsConfig - no need for controller-level annotation
public class NotificationController {
    
    private final NotificationService notificationService;
    
    // Admin Notifications
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<Notification>>> getAllAdminNotifications() {
        List<Notification> notifications = notificationService.getAllAdminNotifications();
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }
    
    @GetMapping("/admin/unread")
    public ResponseEntity<ApiResponse<List<Notification>>> getUnreadAdminNotifications() {
        List<Notification> notifications = notificationService.getUnreadAdminNotifications();
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }
    
    @GetMapping("/admin/count")
    public ResponseEntity<ApiResponse<Long>> getUnreadAdminCount() {
        Long count = notificationService.getUnreadAdminNotificationCount();
        return ResponseEntity.ok(ApiResponse.success(count));
    }
    
    @PutMapping("/admin/{id}/read")
    public ResponseEntity<ApiResponse<Object>> markAdminNotificationAsRead(@PathVariable Long id) {
        notificationService.markAdminNotificationAsRead(id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }
    
    @DeleteMapping("/admin")
    public ResponseEntity<ApiResponse<Object>> clearAllAdminNotifications() {
        notificationService.clearAllAdminNotifications();
        return ResponseEntity.ok(ApiResponse.success("All notifications cleared", null));
    }
    
    // Buyer Notifications
    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<ApiResponse<List<BuyerNotification>>> getBuyerNotifications(@PathVariable Long buyerId) {
        List<BuyerNotification> notifications = notificationService.getBuyerNotifications(buyerId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }
    
    @GetMapping("/buyer/{buyerId}/unread")
    public ResponseEntity<ApiResponse<List<BuyerNotification>>> getUnreadBuyerNotifications(@PathVariable Long buyerId) {
        List<BuyerNotification> notifications = notificationService.getUnreadBuyerNotifications(buyerId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }
    
    @GetMapping("/buyer/{buyerId}/count")
    public ResponseEntity<ApiResponse<Long>> getUnreadBuyerCount(@PathVariable Long buyerId) {
        Long count = notificationService.getUnreadBuyerNotificationCount(buyerId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }
    
    @PutMapping("/buyer/{id}/read")
    public ResponseEntity<ApiResponse<Object>> markBuyerNotificationAsRead(@PathVariable Long id) {
        notificationService.markBuyerNotificationAsRead(id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }
}

