package com.naturaldrops.dto.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class UpdateOrderRequest {

    // Editable customer details
    private String deliveryAddress;
    private String buyerPhone;

    // Optional: who edited (used for stock history notes)
    private String updatedBy;

    @NotEmpty(message = "Order must contain at least one item")
    private List<UpdateOrderItemRequest> items;

    @Data
    public static class UpdateOrderItemRequest {
        @NotNull(message = "menuItemId is required")
        private Long menuItemId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;
    }
}


