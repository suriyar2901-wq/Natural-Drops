package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_claims")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "claimant_user_id", nullable = false)
    private Long claimantUserId;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "seller_user_id")
    private Long sellerUserId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 20)
    private String method = "UPI";

    @Column(name = "upi_reference", length = 80)
    private String upiReference;

    @Column(length = 20)
    private String plan;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 50)
    private String resolvedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Kind {
        SUBSCRIPTION,
        ORDER_BILL
    }

    public enum Status {
        PENDING,
        CONFIRMED,
        REJECTED
    }
}
