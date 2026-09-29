package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_code", unique = true, nullable = false, length = 30)
    private String transactionCode;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerSubscription.Plan plan;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "plan_price_snapshot", precision = 10, scale = 2)
    private BigDecimal planPriceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerSubscription.PaymentStatus status = SellerSubscription.PaymentStatus.PENDING;

    @Column(name = "gateway_ref", length = 80)
    private String gatewayRef;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Method {
        CASH,
        UPI,
        GATEWAY
    }
}
