package com.naturaldrops.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;
    
    @Column(name = "menu_item_id")
    private Long menuItemId;
    
    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;
    
    @Column(name = "cart_quantity", nullable = false)
    private Integer cartQuantity;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
}

