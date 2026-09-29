package com.naturaldrops.entity;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_can_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Integer changeAmount;

    @Column(length = 200)
    private String copy;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
