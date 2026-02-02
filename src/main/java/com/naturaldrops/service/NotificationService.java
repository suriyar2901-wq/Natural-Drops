package com.naturaldrops.service;

import com.naturaldrops.entity.BuyerNotification;
import com.naturaldrops.entity.Notification;
import com.naturaldrops.entity.Order;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.BuyerNotificationRepository;
import com.naturaldrops.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final BuyerNotificationRepository buyerNotificationRepository;
    private final PushNotificationService pushNotificationService;
    
    // Admin Notifications
    public List<Notification> getAllAdminNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }
    
    public List<Notification> getUnreadAdminNotifications() {
        return notificationRepository.findByIsReadOrderByCreatedAtDesc(false);
    }
    
    public Long getUnreadAdminNotificationCount() {
        return notificationRepository.countByIsRead(false);
    }
    
    @Transactional
    public void createAdminNotification(Order order) {
        Notification notification = new Notification();
        notification.setOrderId(order.getId());
        notification.setCustomerName(order.getBuyerName());
        notification.setTotal(order.getTotal());
        notification.setItemCount(order.getItems().size());
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        
        notificationRepository.save(notification);
    }
    
    @Transactional
    public void markAdminNotificationAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }
    
    @Transactional
    public void clearAllAdminNotifications() {
        notificationRepository.deleteAll();
    }
    
    // Buyer Notifications
    public List<BuyerNotification> getBuyerNotifications(Long buyerId) {
        return buyerNotificationRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }
    
    public List<BuyerNotification> getUnreadBuyerNotifications(Long buyerId) {
        return buyerNotificationRepository.findByBuyerIdAndIsReadOrderByCreatedAtDesc(buyerId, false);
    }
    
    public Long getUnreadBuyerNotificationCount(Long buyerId) {
        return buyerNotificationRepository.countByBuyerIdAndIsRead(buyerId, false);
    }
    
    @Transactional
    public void createBuyerNotification(Order order) {
        createBuyerNotification(order, order.getStatus());
    }
    
    @Transactional
    public void createBuyerNotification(Order order, Order.OrderStatus status) {
        BuyerNotification notification = new BuyerNotification();
        notification.setBuyerId(order.getBuyerId());
        notification.setOrderId(order.getId());
        
        // Create message and title based on status
        // Title should be "Order Status Updated" as per requirements
        String title = "Order Status Updated";
        String message;
        switch (status) {
            case confirmed:
                message = "Your order has been confirmed by the seller.";
                break;
            case processing:
                if (order.getDeliveryTimeMinutes() != null) {
                    message = "Your order #" + order.getId() + " is on the way! Expected delivery in " + order.getDeliveryTimeMinutes() + " minutes.";
                } else {
                    message = "Your order #" + order.getId() + " is on the way!";
                }
                break;
            case canceled:
                message = "Your order has been cancelled by the seller.";
                break;
            case delivered:
                message = "Your order #" + order.getId() + " has been delivered! Thank you for your purchase.";
                break;
            default:
                message = "Your order #" + order.getId() + " status has been updated to " + status + ".";
        }
        
        notification.setMessage(message);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        
        buyerNotificationRepository.save(notification);
        
        // Send push notification to buyer
        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("orderId", order.getId());
            notificationData.put("status", status.toString());
            notificationData.put("type", "order_update");
            
            pushNotificationService.sendNotificationToBuyer(
                    order.getBuyerId(),
                    title,
                    message,
                    notificationData
            );
        } catch (Exception e) {
            // Log error but don't fail the transaction
            // Push notification failure shouldn't prevent order status update
            System.err.println("Failed to send push notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Transactional
    public void markBuyerNotificationAsRead(Long notificationId) {
        BuyerNotification notification = buyerNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
        buyerNotificationRepository.save(notification);
    }
}

