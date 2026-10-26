package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "cashfree_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cashfree_cf_order_id", columnNames = "cf_order_id")
        },
        indexes = {
                @Index(name = "idx_cashfree_nd_order", columnList = "order_id"),
                @Index(name = "idx_cashfree_buyer", columnList = "buyer_user_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashfreePayment {

    public enum Status {
        CREATED,
        PENDING,
        SUCCESS,
        FAILED,
        EXPIRED,
        USER_DROPPED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Natural Drops order id */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "buyer_user_id", nullable = false)
    private Long buyerUserId;

    @Column(name = "seller_user_id")
    private Long sellerUserId;

    /** Merchant order id sent to Cashfree (unique string) */
    @Column(name = "cf_order_id", nullable = false, length = 64)
    private String cfOrderId;

    @Column(name = "cf_payment_id", length = 64)
    private String cfPaymentId;

    @Column(name = "payment_session_id", length = 255)
    private String paymentSessionId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CREATED;

    @Column(name = "cf_order_status", length = 40)
    private String cfOrderStatus;

    @Column(name = "environment", length = 20)
    private String environment;

    /** True after Natural Drops order/ledger was updated for this payment */
    @Column(name = "payment_applied", nullable = false)
    private Boolean paymentApplied = false;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (paymentApplied == null) {
            paymentApplied = false;
        }
        if (currency == null) {
            currency = "INR";
        }
        if (status == null) {
            status = Status.CREATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
