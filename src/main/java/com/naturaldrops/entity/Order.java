package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;
    
    @Column(name = "buyer_name", nullable = false, length = 100)
    private String buyerName;
    
    @Column(name = "buyer_phone", length = 20)
    private String buyerPhone;
    
    @Column(name = "buyer_address", columnDefinition = "TEXT")
    private String buyerAddress;
    
    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;
    
    @Column(name = "latitude", precision = 10, scale = 8)
    private Double latitude;
    
    @Column(name = "longitude", precision = 11, scale = 8)
    private Double longitude;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.pending;
    
    @Column(name = "order_date", nullable = false, updatable = false)
    private LocalDateTime orderDate;
    
    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;
    
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;
    
    @Column(name = "delivery_partner", length = 100)
    private String deliveryPartner;
    
    @Column(name = "estimated_delivery")
    private LocalDateTime estimatedDelivery;
    
    @Column(name = "confirmed_by", length = 50)
    private String confirmedBy;
    
    @Column(name = "delivered_by", length = 50)
    private String deliveredBy;
    
    @Column(name = "delivery_time_minutes")
    private Integer deliveryTimeMinutes;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "delivery_total_seconds")
    private Long deliveryTotalSeconds;
    
    @Column(name = "delivery_start_timestamp")
    private Long deliveryStartTimestamp;
    
    @Column(name = "final_bill_amount", precision = 10, scale = 2)
    private BigDecimal finalBillAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;
    
    @Column(name = "billed_by", length = 50)
    private String billedBy;
    
    @Column(name = "billed_at")
    private LocalDateTime billedAt;
    
    @Column(name = "billing_notes", columnDefinition = "TEXT")
    private String billingNotes;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        orderDate = LocalDateTime.now();
    }
    
    public enum OrderStatus {
        pending, confirmed, processing, delivered, canceled
    }
    
    public enum PaymentStatus {
        PAID, UNPAID, PARTIALLY_PAID
    }
    
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}

