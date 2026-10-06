package com.naturaldrops.service;

import com.naturaldrops.entity.BuyerNotification;
import com.naturaldrops.entity.Notification;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.BuyerNotificationRepository;
import com.naturaldrops.repository.NotificationRepository;
import com.naturaldrops.repository.OrderRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final BuyerNotificationRepository buyerNotificationRepository;
    private final PushNotificationService pushNotificationService;
    private final OrderRepository orderRepository;
    private final SellerNetworkService sellerNetworkService;
    private final UserRepository userRepository;
    
    // Admin Notifications
    public List<Notification> getAllAdminNotifications(User currentUser) {
        return scopeNotifications(notificationRepository.findAllByOrderByCreatedAtDesc(), currentUser);
    }
    
    public List<Notification> getUnreadAdminNotifications(User currentUser) {
        return scopeNotifications(notificationRepository.findByIsReadOrderByCreatedAtDesc(false), currentUser);
    }
    
    public Long getUnreadAdminNotificationCount(User currentUser) {
        return Long.valueOf(getUnreadAdminNotifications(currentUser).size());
    }

    private List<Notification> scopeNotifications(List<Notification> notifications, User currentUser) {
        if (currentUser == null || currentUser.getRole() != User.UserRole.seller) {
            return notifications;
        }
        Set<Long> buyerIds = sellerNetworkService.buyerIdsForSeller(sellerNetworkService.findSellerForUser(currentUser));
        List<Notification> scoped = new ArrayList<Notification>();
        if (notifications == null || notifications.isEmpty() || buyerIds.isEmpty()) {
            return scoped;
        }
        Set<Long> orderIds = new HashSet<Long>();
        for (Notification notification : notifications) {
            if (notification.getOrderId() != null) {
                orderIds.add(notification.getOrderId());
            }
        }
        Map<Long, Long> orderBuyerIds = new HashMap<Long, Long>();
        for (Order order : orderRepository.findAllById(orderIds)) {
            orderBuyerIds.put(order.getId(), order.getBuyerId());
        }
        for (Notification notification : notifications) {
            Long buyerId = orderBuyerIds.get(notification.getOrderId());
            if (buyerId != null && buyerIds.contains(buyerId)) {
                scoped.add(notification);
            }
        }
        return scoped;
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
    public void setOrderNotificationMessage(Long orderId, String message) {
        List<Notification> notes = notificationRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (notes.isEmpty() || message == null) {
            return;
        }
        notes.get(0).setMessage(message);
        notificationRepository.save(notes.get(0));
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
    public void createBuyerBillUpdatedNotification(Order order, BigDecimal previousAmount) {
        BuyerNotification notification = new BuyerNotification();
        notification.setBuyerId(order.getBuyerId());
        notification.setOrderId(order.getId());

        String statusLabel;
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            statusLabel = "fully paid";
        } else if (order.getPaymentStatus() == Order.PaymentStatus.UNPAID) {
            statusLabel = "unpaid";
        } else {
            statusLabel = "partially paid";
        }

        String title = "Bill Updated";
        StringBuilder message = new StringBuilder();
        message.append("Your order #").append(order.getId()).append(" bill was updated");
        if (previousAmount != null && order.getFinalBillAmount() != null) {
            message.append(" from ₹").append(previousAmount.toPlainString());
        }
        if (order.getFinalBillAmount() != null) {
            message.append(" to ₹").append(order.getFinalBillAmount().toPlainString());
        }
        message.append(". It is now ").append(statusLabel).append(".");
        if (order.getBillingNotes() != null && !order.getBillingNotes().trim().isEmpty()) {
            message.append(" Note: ").append(order.getBillingNotes().trim());
        }

        notification.setMessage(message.toString());
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        buyerNotificationRepository.save(notification);

        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("orderId", order.getId());
            notificationData.put("status", order.getStatus() != null ? order.getStatus().toString() : "");
            notificationData.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().toString() : "");
            notificationData.put("type", "bill_update");
            pushNotificationService.sendNotificationToBuyer(
                    order.getBuyerId(),
                    title,
                    message.toString(),
                    notificationData
            );
        } catch (Exception e) {
            System.err.println("Failed to send bill update push notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendDeliveryDayBeforeAlert(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId).orElse(null);
        if (order == null || Boolean.TRUE.equals(order.getDeliveryReminderSent())) {
            return;
        }
        String dateLabel = order.getScheduledDeliveryDate() != null
                ? order.getScheduledDeliveryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "tomorrow";
        String timeLabel = "";
        if (order.getEstimatedDelivery() != null) {
            timeLabel = " at " + order.getEstimatedDelivery().format(DateTimeFormatter.ofPattern("hh:mm a"));
        }
        boolean regular = order.getBillingNotes() != null
                && order.getBillingNotes().toLowerCase().contains("regular");
        String title = regular ? "Regular order tomorrow" : "Delivery tomorrow";
        String message = (regular ? "Regular order: Order #" : "Delivery reminder: Order #")
                + order.getId() + " for " + order.getBuyerName()
                + " is scheduled for delivery tomorrow (" + dateLabel + timeLabel + ").";

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("orderId", order.getId());
        data.put("type", "delivery_reminder");

        User buyer = order.getBuyerId() != null ? userRepository.findById(order.getBuyerId()).orElse(null) : null;
        if (buyer != null && buyer.getRole() == User.UserRole.buyer) {
            BuyerNotification buyerNotification = new BuyerNotification();
            buyerNotification.setBuyerId(buyer.getId());
            buyerNotification.setOrderId(order.getId());
            buyerNotification.setMessage(message);
            buyerNotification.setIsRead(false);
            buyerNotification.setCreatedAt(LocalDateTime.now());
            buyerNotificationRepository.save(buyerNotification);
            try {
                pushNotificationService.sendNotificationToBuyer(buyer.getId(), title, message, data);
            } catch (Exception e) {
                System.err.println("Failed to push delivery reminder to buyer: " + e.getMessage());
            }
        }

        if (order.getSellerUserId() != null) {
            sellerNetworkService.postInboxMessage(order.getSellerUserId(), order.getBuyerId(), title, message);
            Notification sellerNotification = new Notification();
            sellerNotification.setOrderId(order.getId());
            sellerNotification.setCustomerName(order.getBuyerName());
            sellerNotification.setTotal(order.getTotal());
            sellerNotification.setItemCount(order.getItems() != null ? order.getItems().size() : 0);
            sellerNotification.setMessage(message);
            sellerNotification.setIsRead(false);
            sellerNotification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(sellerNotification);
            try {
                pushNotificationService.sendNotificationToBuyer(order.getSellerUserId(), title, message, data);
            } catch (Exception e) {
                System.err.println("Failed to push delivery reminder to seller: " + e.getMessage());
            }
        }
        order.setDeliveryReminderSent(Boolean.TRUE);
        orderRepository.save(order);
    }

    @Transactional
    public void markBuyerNotificationAsRead(Long notificationId) {
        BuyerNotification notification = buyerNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setIsRead(true);
        buyerNotificationRepository.save(notification);
    }
}

