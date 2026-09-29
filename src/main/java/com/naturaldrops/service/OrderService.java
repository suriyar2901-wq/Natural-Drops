package com.naturaldrops.service;

import com.naturaldrops.dto.request.CreateOrderRequest;
import com.naturaldrops.dto.request.UpdateOrderRequest;
import com.naturaldrops.entity.MenuItem;
import com.naturaldrops.entity.Order;
import com.naturaldrops.entity.OrderItem;
import com.naturaldrops.entity.OrderStatusHistory;
import com.naturaldrops.entity.CanEvent;
import com.naturaldrops.entity.ShopCustomer;
import com.naturaldrops.entity.User;
import com.naturaldrops.exception.BadRequestException;
import com.naturaldrops.exception.ResourceNotFoundException;
import com.naturaldrops.repository.CanEventRepository;
import com.naturaldrops.repository.OrderRepository;
import com.naturaldrops.repository.OrderStatusHistoryRepository;
import com.naturaldrops.repository.ShopCustomerRepository;
import com.naturaldrops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final NotificationService notificationService;
    private final MenuService menuService;
    private final SellerNetworkService sellerNetworkService;
    private final ShopCustomerRepository shopCustomerRepository;
    private final CanEventRepository canEventRepository;
    private final UserRepository userRepository;
    
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc();
    }

    public List<Order> getOrdersFiltered(String status, String fromDate, String toDate) {
        Order.OrderStatus parsedStatus = null;
        if (status != null && !status.trim().isEmpty()) {
            parsedStatus = Order.OrderStatus.valueOf(status.trim().toLowerCase());
        }

        LocalDateTime from = null;
        LocalDateTime to = null;
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            LocalDate d = LocalDate.parse(fromDate.trim()); // yyyy-MM-dd
            from = d.atStartOfDay();
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            LocalDate d = LocalDate.parse(toDate.trim()); // yyyy-MM-dd
            to = d.atTime(LocalTime.MAX);
        }

        // No filters -> all
        if (parsedStatus == null && from == null && to == null) {
            return getAllOrders();
        }

        // Normalize missing bound(s)
        if (from != null && to == null) {
            to = LocalDate.now().atTime(LocalTime.MAX);
        } else if (from == null && to != null) {
            from = LocalDate.of(2000, 1, 1).atStartOfDay();
        }

        // Date range only
        if (parsedStatus == null && from != null && to != null) {
            return orderRepository.findOrdersBetweenDates(from, to);
        }

        // Status only
        if (parsedStatus != null && from == null && to == null) {
            return orderRepository.findByStatusOrderByOrderDateDesc(parsedStatus);
        }

        // Status + date range
        if (parsedStatus != null && from != null && to != null) {
            return orderRepository.findOrdersByStatusBetweenDates(parsedStatus, from, to);
        }

        return getAllOrders();
    }
    
    public Order getOrderById(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }
    
    public List<Order> getOrdersByBuyerId(Long buyerId) {
        return orderRepository.findByBuyerIdOrderByOrderDateDesc(buyerId);
    }
    
    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatusOrderByOrderDateDesc(status);
    }
    
    public List<Order> getOrdersByBuyerIdAndStatus(Long buyerId, Order.OrderStatus status) {
        return orderRepository.findByBuyerIdAndStatusOrderByOrderDateDesc(buyerId, status);
    }
    
    public List<Order> getTodayOrders() {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        return orderRepository.findByOrderDateAfterOrderByOrderDateDesc(startOfDay);
    }
    
    public List<Order> getWeekOrders() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        return orderRepository.findByOrderDateAfterOrderByOrderDateDesc(weekAgo);
    }
    
    public List<Order> getMonthOrders() {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime monthEnd = LocalDateTime.now();
        return orderRepository.findOrdersBetweenDates(monthStart, monthEnd);
    }
    
    public List<OrderStatusHistory> getOrderStatusHistory(Long orderId) {
        return orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
    }
    
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setBuyerId(request.getBuyerId());
        order.setBuyerName(request.getBuyerName());
        order.setBuyerPhone(request.getBuyerPhone());
        order.setBuyerAddress(request.getBuyerAddress());
        
        // Set delivery address and coordinates if provided
        if (request.getDeliveryAddress() != null && !request.getDeliveryAddress().isEmpty()) {
            order.setDeliveryAddress(request.getDeliveryAddress());
        } else {
            // Use buyer address as fallback
            order.setDeliveryAddress(request.getBuyerAddress());
        }
        
        if (request.getLatitude() != null && request.getLongitude() != null) {
            order.setLatitude(request.getLatitude());
            order.setLongitude(request.getLongitude());
        }
        
        order.setTotal(request.getTotal());
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            order.setBillingNotes(request.getNote().trim());
        }
        order.setStatus(Order.OrderStatus.pending);
        order.setOrderDate(LocalDateTime.now());
        applyBuyerDeliverySlot(order, request);
        
        // Add order items
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setMenuItemId(itemRequest.getMenuItemId());
            orderItem.setItemName(itemRequest.getItemName());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setRate(itemRequest.getRate());
            orderItem.setCartQuantity(itemRequest.getCartQuantity());
            orderItem.setSubtotal(itemRequest.getSubtotal());
            order.addItem(orderItem);
        }
        
        Order savedOrder = orderRepository.save(order);
        
        // Record status history
        recordStatusChange(savedOrder.getId(), null, Order.OrderStatus.pending, "system", "Order created");
        
        // Create notification for admin
        notificationService.createAdminNotification(savedOrder);
        if (request.getScheduledDeliveryDate() != null && !request.getScheduledDeliveryDate().trim().isEmpty()) {
            issueTwentyLitreCans(savedOrder);
        }
        
        return savedOrder;
    }

    private void applyBuyerDeliverySlot(Order order, CreateOrderRequest request) {
        if (request.getScheduledDeliveryDate() == null || request.getScheduledDeliveryDate().trim().isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate deliveryDate;
        try {
            deliveryDate = LocalDate.parse(request.getScheduledDeliveryDate().trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Choose a valid delivery date");
        }
        if (deliveryDate.isBefore(today)) {
            throw new BadRequestException("Delivery date cannot be in the past");
        }
        LocalTime deliveryTime = LocalTime.of(9, 0);
        if (request.getDeliveryTime() != null && !request.getDeliveryTime().trim().isEmpty()) {
            try {
                deliveryTime = LocalTime.parse(request.getDeliveryTime().trim());
            } catch (DateTimeParseException ex) {
                throw new BadRequestException("Choose a valid delivery time");
            }
        }
        if (deliveryDate.equals(today) && !deliveryTime.isAfter(LocalTime.now())) {
            throw new BadRequestException("Choose a future delivery time");
        }
        order.setScheduledDeliveryDate(deliveryDate);
        order.setEstimatedDelivery(deliveryDate.atTime(deliveryTime));
        order.setDeliveryReminderSent(Boolean.FALSE);
        order.setSellerUserId(sellerNetworkService.sellerUserIdForBuyer(request.getBuyerId()));
    }

    private void issueTwentyLitreCans(Order order) {
        int cans = 0;
        for (OrderItem item : order.getItems()) {
            if (item.getItemName() != null && item.getItemName().toLowerCase().contains("20")) {
                cans += item.getQuantity() != null ? item.getQuantity() : 0;
            }
        }
        if (cans <= 0 || order.getBuyerId() == null) {
            return;
        }
        ShopCustomer customer = shopCustomerRepository.findByBuyerUserId(order.getBuyerId()).orElse(null);
        if (customer == null && order.getBuyerPhone() != null && order.getBuyerPhone().trim().length() == 10) {
            String phone = order.getBuyerPhone().trim();
            Long sellerUserId = order.getSellerUserId();
            for (ShopCustomer match : shopCustomerRepository.findByMobile(phone)) {
                if (sellerUserId == null || sellerUserId.equals(match.getSellerUserId())) {
                    customer = match;
                    break;
                }
            }
        }
        if (customer == null) {
            return;
        }
        if (customer.getBuyerUserId() == null) {
            User buyer = userRepository.findById(order.getBuyerId()).orElse(null);
            if (buyer != null && buyer.getRole() == User.UserRole.buyer) {
                customer.setBuyerUserId(buyer.getId());
            }
        }
        customer.setEmptyCans((customer.getEmptyCans() == null ? 0 : customer.getEmptyCans()) + cans);
        shopCustomerRepository.save(customer);
        CanEvent event = new CanEvent();
        event.setCustomerId(customer.getId());
        event.setChangeAmount(cans);
        event.setCopy("Issued " + cans + " can(s) with order #" + order.getId());
        event.setOccurredAt(LocalDateTime.now());
        canEventRepository.save(event);
    }

    @Transactional
    public Order schedulePhoneDelivery(Long orderId, LocalDate deliveryDate, Long sellerUserId) {
        Order order = getOrderById(orderId);
        order.setScheduledDeliveryDate(deliveryDate);
        order.setDeliveryReminderSent(Boolean.FALSE);
        order.setSellerUserId(sellerUserId);
        order.setEstimatedDelivery(deliveryDate.atTime(9, 0));
        return orderRepository.save(order);
    }

    /**
     * Update an order (items + delivery address + buyer phone) for Pending/Confirmed orders only.
     * - Pending: just updates items and recalculates totals
     * - Confirmed: restores previous stock, applies new items, then deducts stock again
     */
    @Transactional
    public Order updateOrder(Long id, UpdateOrderRequest request) {
        Order order = getOrderById(id);

        if (order.getStatus() == Order.OrderStatus.delivered || order.getStatus() == Order.OrderStatus.canceled) {
            throw new IllegalStateException("Delivered or canceled orders cannot be edited");
        }
        if (order.getStatus() != Order.OrderStatus.pending && order.getStatus() != Order.OrderStatus.confirmed) {
            throw new IllegalStateException("Only pending or confirmed orders can be edited");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        final String updatedBy = (request.getUpdatedBy() != null && !request.getUpdatedBy().trim().isEmpty())
                ? request.getUpdatedBy().trim()
                : "seller";

        // If order already confirmed, revert old stock impact first (so inventory remains correct)
        if (order.getStatus() == Order.OrderStatus.confirmed) {
            for (OrderItem item : order.getItems()) {
                if (item.getMenuItemId() != null) {
                    // cartQuantity is what we originally deducted
                    menuService.restoreStock(item.getMenuItemId(), item.getCartQuantity(), order.getId(), updatedBy);
                }
            }
        }

        // Update customer details
        if (request.getDeliveryAddress() != null) {
            String addr = request.getDeliveryAddress().trim();
            order.setDeliveryAddress(addr.isEmpty() ? null : addr);
        }
        if (request.getBuyerPhone() != null) {
            String phone = request.getBuyerPhone().trim();
            order.setBuyerPhone(phone.isEmpty() ? null : phone);
        }

        // Replace order items
        order.getItems().clear();

        BigDecimal subtotalSum = BigDecimal.ZERO;
        for (UpdateOrderRequest.UpdateOrderItemRequest it : request.getItems()) {
            if (it.getMenuItemId() == null) {
                throw new IllegalArgumentException("menuItemId is required");
            }
            if (it.getQuantity() == null || it.getQuantity() < 1) {
                throw new IllegalArgumentException("quantity must be at least 1");
            }

            MenuItem menuItem = menuService.getMenuItemById(it.getMenuItemId());
            BigDecimal rate = menuItem.getRate() != null ? menuItem.getRate() : BigDecimal.ZERO;
            int qty = it.getQuantity();
            BigDecimal itemSubtotal = rate.multiply(BigDecimal.valueOf(qty));

            OrderItem orderItem = new OrderItem();
            orderItem.setMenuItemId(menuItem.getId());
            orderItem.setItemName(menuItem.getName());
            orderItem.setQuantity(qty);
            orderItem.setCartQuantity(qty);
            orderItem.setRate(rate);
            orderItem.setSubtotal(itemSubtotal);
            order.addItem(orderItem);

            subtotalSum = subtotalSum.add(itemSubtotal);
        }

        // Recalculate total similar to buyer checkout: 5% tax + fixed delivery 20
        BigDecimal tax = subtotalSum.multiply(new BigDecimal("0.05"));
        BigDecimal deliveryCharge = new BigDecimal("20.00");
        BigDecimal total = subtotalSum.add(tax).add(deliveryCharge).setScale(2, RoundingMode.HALF_UP);
        order.setTotal(total);

        Order saved = orderRepository.save(order);

        // If confirmed, apply new stock impact
        if (saved.getStatus() == Order.OrderStatus.confirmed) {
            for (OrderItem item : saved.getItems()) {
                if (item.getMenuItemId() != null) {
                    menuService.deductStock(item.getMenuItemId(), item.getCartQuantity(), saved.getId(), updatedBy);
                }
            }
        }

        return saved;
    }
    
    @Transactional
    public Order confirmOrder(Long id, String confirmedBy) {
        Order order = getOrderById(id);
        
        if (order.getStatus() != Order.OrderStatus.pending) {
            throw new IllegalStateException("Only pending orders can be confirmed");
        }
        
        boolean phoneOrder = order.getSellerUserId() != null
                || (order.getDeliveryAddress() != null && order.getDeliveryAddress().contains("Phone Order"));
        StringBuilder confirmNote = new StringBuilder(phoneOrder
                ? "Phone order confirmed"
                : "Order confirmed, stock deducted");

        for (OrderItem item : order.getItems()) {
            if (item.getMenuItemId() == null) {
                continue;
            }
            int qty = item.getCartQuantity() != null ? item.getCartQuantity()
                    : (item.getQuantity() != null ? item.getQuantity() : 0);
            if (qty <= 0) {
                continue;
            }
            if (phoneOrder) {
                int deducted = menuService.deductAvailableStock(item.getMenuItemId(), qty, order.getId(), confirmedBy);
                if (deducted < qty) {
                    confirmNote.append(". Stock short for ").append(item.getItemName())
                            .append(" (needed ").append(qty).append(", deducted ").append(deducted).append(")");
                }
            } else {
                menuService.deductStock(item.getMenuItemId(), qty, order.getId(), confirmedBy);
            }
        }
        
        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.confirmed);
        order.setConfirmedBy(confirmedBy);
        order.setStatusUpdatedAt(LocalDateTime.now());
        
        Order updatedOrder = orderRepository.save(order);
        recordStatusChange(id, oldStatus, Order.OrderStatus.confirmed, confirmedBy, confirmNote.toString());
        
        // Create buyer notification for confirmed order (don't fail if notification fails)
        try {
            notificationService.createBuyerNotification(updatedOrder, Order.OrderStatus.confirmed);
        } catch (Exception e) {
            // Log but don't fail the transaction
            System.err.println("Failed to create buyer notification: " + e.getMessage());
            e.printStackTrace();
        }
        
        return updatedOrder;
    }
    
    @Transactional
    public Order markAsProcessing(Long id, String trackingNumber, String deliveryPartner, LocalDateTime estimatedDelivery, String updatedBy) {
        Order order = getOrderById(id);
        
        if (order.getStatus() != Order.OrderStatus.confirmed) {
            throw new IllegalStateException("Only confirmed orders can be marked as processing");
        }
        
        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.processing);
        order.setTrackingNumber(trackingNumber);
        order.setDeliveryPartner(deliveryPartner);
        order.setEstimatedDelivery(estimatedDelivery);
        order.setStatusUpdatedAt(LocalDateTime.now());
        
        Order updatedOrder = orderRepository.save(order);
        recordStatusChange(id, oldStatus, Order.OrderStatus.processing, updatedBy, 
                          "Order in processing with tracking: " + trackingNumber);
        
        return updatedOrder;
    }
    
    @Transactional
    public Order setOnTheWay(Long id, Long deliveryTotalSeconds, String updatedBy) {
        Order order = getOrderById(id);
        
        if (order.getStatus() != Order.OrderStatus.confirmed) {
            throw new IllegalStateException("Only confirmed orders can be set to On The Way");
        }
        
        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.processing);
        // Keep legacy minutes field for old clients (rounded up)
        long totalSeconds = deliveryTotalSeconds != null ? deliveryTotalSeconds : 0L;
        int legacyMinutes = (int) Math.max(1, Math.round(Math.ceil(totalSeconds / 60.0)));
        order.setDeliveryTimeMinutes(legacyMinutes);
        order.setStartTime(LocalDateTime.now());
        
        // Store total seconds and epoch timestamp for countdown calculation
        long startTimestamp = System.currentTimeMillis() / 1000; // Epoch time in seconds
        order.setDeliveryTotalSeconds(totalSeconds);
        order.setDeliveryStartTimestamp(startTimestamp);
        
        order.setStatusUpdatedAt(LocalDateTime.now());
        
        Order updatedOrder = orderRepository.save(order);
        recordStatusChange(id, oldStatus, Order.OrderStatus.processing, updatedBy, 
                          "Order set to On The Way with delivery time: " + totalSeconds + " seconds");
        
        // Create buyer notification
        try {
            notificationService.createBuyerNotification(updatedOrder, Order.OrderStatus.processing);
        } catch (Exception e) {
            System.err.println("Failed to create buyer notification: " + e.getMessage());
            e.printStackTrace();
        }
        
        return updatedOrder;
    }
    
    @Transactional
    public Order markAsDelivered(Long id, String deliveredBy) {
        Order order = getOrderById(id);
        
        // If already delivered, return the order (idempotent operation)
        if (order.getStatus() == Order.OrderStatus.delivered) {
            return order;
        }
        
        // Only processing (On The Way) orders can be marked as delivered
        if (order.getStatus() != Order.OrderStatus.processing) {
            throw new IllegalStateException(
                String.format("Only orders in 'processing' (On The Way) status can be marked as delivered. Current status: %s", 
                    order.getStatus())
            );
        }
        
        // Require bill to be set before delivery
        if (order.getFinalBillAmount() == null) {
            throw new IllegalStateException(
                "Bill must be finalized before marking order as delivered. Please add/edit the bill first."
            );
        }
        
        Order.OrderStatus oldStatus = order.getStatus();
        order.setStatus(Order.OrderStatus.delivered);
        order.setDeliveredBy(deliveredBy);
        order.setStatusUpdatedAt(LocalDateTime.now());
        
        Order updatedOrder = orderRepository.save(order);
        recordStatusChange(id, oldStatus, Order.OrderStatus.delivered, deliveredBy, 
            String.format("Order delivered. Final bill: ₹%s, Payment status: %s", 
                order.getFinalBillAmount(), order.getPaymentStatus()));
        
        // Create buyer notification
        try {
            notificationService.createBuyerNotification(updatedOrder);
        } catch (Exception e) {
            // Log error but don't fail the delivery operation
            System.err.println("Failed to create buyer notification: " + e.getMessage());
            e.printStackTrace();
        }
        
        return updatedOrder;
    }
    
    @Transactional
    public Order cancelOrder(Long id, String canceledBy, String reason) {
        Order order = getOrderById(id);
        
        if (order.getStatus() == Order.OrderStatus.delivered) {
            throw new IllegalStateException("Delivered orders cannot be canceled");
        }
        
        Order.OrderStatus oldStatus = order.getStatus();
        
        // Restore stock if order was confirmed or processing
        if (oldStatus == Order.OrderStatus.confirmed || oldStatus == Order.OrderStatus.processing) {
            for (OrderItem item : order.getItems()) {
                if (item.getMenuItemId() != null) {
                    menuService.restoreStock(item.getMenuItemId(), item.getCartQuantity(), order.getId(), canceledBy);
                }
            }
        }
        
        order.setStatus(Order.OrderStatus.canceled);
        order.setStatusUpdatedAt(LocalDateTime.now());
        
        Order updatedOrder = orderRepository.save(order);
        recordStatusChange(id, oldStatus, Order.OrderStatus.canceled, canceledBy, 
                          "Order canceled: " + (reason != null ? reason : "No reason provided"));
        
        // Create buyer notification for canceled order (don't fail if notification fails)
        try {
            notificationService.createBuyerNotification(updatedOrder, Order.OrderStatus.canceled);
        } catch (Exception e) {
            // Log but don't fail the transaction
            System.err.println("Failed to create buyer notification: " + e.getMessage());
            e.printStackTrace();
        }
        
        return updatedOrder;
    }
    
    @Transactional
    public Order updateOrderStatus(Long id, Order.OrderStatus status) {
        Order order = getOrderById(id);
        Order.OrderStatus oldStatus = order.getStatus();
        
        order.setStatus(status);
        order.setStatusUpdatedAt(LocalDateTime.now());
        
        Order updatedOrder = orderRepository.save(order);
        recordStatusChange(id, oldStatus, status, "system", "Status updated");
        
        // Create buyer notification based on new status
        if (oldStatus != status) {
            if (status == Order.OrderStatus.confirmed) {
                notificationService.createBuyerNotification(updatedOrder, Order.OrderStatus.confirmed);
            } else if (status == Order.OrderStatus.canceled) {
                notificationService.createBuyerNotification(updatedOrder, Order.OrderStatus.canceled);
            } else if (status == Order.OrderStatus.delivered) {
                notificationService.createBuyerNotification(updatedOrder, Order.OrderStatus.delivered);
            }
        }
        
        return updatedOrder;
    }
    
    @Transactional
    public Order updateOrderBill(Long id, com.naturaldrops.dto.request.UpdateOrderBillRequest request) {
        Order order = getOrderById(id);

        if (order.getStatus() == Order.OrderStatus.canceled) {
            throw new IllegalStateException("Canceled orders cannot be edited");
        }
        if (order.getPaymentStatus() == Order.PaymentStatus.PAID) {
            throw new IllegalStateException("Paid bills cannot be edited");
        }

        Order.PaymentStatus previousPaymentStatus = order.getPaymentStatus();
        java.math.BigDecimal previousBillAmount = order.getFinalBillAmount();

        boolean openPayment = previousPaymentStatus == Order.PaymentStatus.PARTIALLY_PAID
                || previousPaymentStatus == Order.PaymentStatus.UNPAID;
        boolean firstBillOnTheWay = previousPaymentStatus == null
                && order.getStatus() == Order.OrderStatus.processing;
        if (!openPayment && !firstBillOnTheWay) {
            throw new IllegalStateException(
                String.format("Bill can only be updated for unpaid or partially paid orders. Current status: %s, payment: %s",
                    order.getStatus(), previousPaymentStatus)
            );
        }
        
        // Validate bill amount doesn't exceed original order total
        if (request.getFinalBillAmount().compareTo(order.getTotal()) > 0) {
            throw new IllegalArgumentException(
                String.format("Final bill amount (₹%s) cannot exceed original order total (₹%s)", 
                    request.getFinalBillAmount(), order.getTotal())
            );
        }
        
        // Set bill details
        order.setFinalBillAmount(request.getFinalBillAmount());
        order.setBillingNotes(request.getBillingNotes());
        order.setBilledBy(request.getBilledBy() != null ? request.getBilledBy() : "seller");
        order.setBilledAt(LocalDateTime.now());
        
        // Determine payment status based on bill amount
        int comparison = request.getFinalBillAmount().compareTo(order.getTotal());
        if (comparison == 0) {
            // Full payment
            order.setPaymentStatus(Order.PaymentStatus.PAID);
        } else if (comparison < 0) {
            // Partial payment
            if (request.getFinalBillAmount().compareTo(java.math.BigDecimal.ZERO) == 0) {
                order.setPaymentStatus(Order.PaymentStatus.UNPAID);
            } else {
                order.setPaymentStatus(Order.PaymentStatus.PARTIALLY_PAID);
            }
        }
        
        Order updatedOrder = orderRepository.save(order);
        
        // Record in status history
        String notes = String.format("Bill updated: ₹%s (Original: ₹%s). Payment Status: %s", 
            request.getFinalBillAmount(), order.getTotal(), order.getPaymentStatus());
        if (request.getBillingNotes() != null && !request.getBillingNotes().trim().isEmpty()) {
            notes += ". Notes: " + request.getBillingNotes();
        }
        recordStatusChange(id, order.getStatus(), order.getStatus(), 
            request.getBilledBy() != null ? request.getBilledBy() : "seller", notes);
        
        // Editing a partially paid bill notifies the buyer. A fully paid bill is locked above.
        try {
            if (previousPaymentStatus == Order.PaymentStatus.PARTIALLY_PAID) {
                notificationService.createBuyerBillUpdatedNotification(updatedOrder, previousBillAmount);
            } else if (order.getStatus() == Order.OrderStatus.processing) {
                notificationService.createBuyerNotification(updatedOrder);
            }
        } catch (Exception e) {
            System.err.println("Failed to create buyer notification: " + e.getMessage());
            e.printStackTrace();
        }
        
        return updatedOrder;
    }
    
    private void recordStatusChange(Long orderId, Order.OrderStatus oldStatus, Order.OrderStatus newStatus, 
                                    String changedBy, String notes) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderId);
        history.setOldStatus(oldStatus != null ? oldStatus.toString() : null);
        history.setNewStatus(newStatus.toString());
        history.setChangedBy(changedBy);
        history.setChangedAt(LocalDateTime.now());
        history.setNotes(notes);
        orderStatusHistoryRepository.save(history);
    }
}

