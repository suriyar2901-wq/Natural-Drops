package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shop_ledger_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 20)
    private String method;

    @Column(length = 80)
    private String reference;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    public enum Kind {
        OPENING,
        BILL,
        RECEIPT
    }
}
