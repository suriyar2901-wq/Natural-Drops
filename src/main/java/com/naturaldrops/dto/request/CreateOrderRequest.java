package com.naturaldrops.dto.request;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    
    @NotNull(message = "Buyer ID is required")
    private Long buyerId;
    
    @NotNull(message = "Buyer name is required")
    private String buyerName;
    
    private String buyerPhone;
    private String buyerAddress;
    
    private String deliveryAddress;
    private Double latitude;
    private Double longitude;
    
    @NotNull(message = "Total is required")
    private BigDecimal total;
    
    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemRequest> items;
    
    @Data
    public static class OrderItemRequest {
        private Long menuItemId;
        private String itemName;
        private Integer quantity;
        private BigDecimal rate;
        private Integer cartQuantity;
        private BigDecimal subtotal;
    }
}

