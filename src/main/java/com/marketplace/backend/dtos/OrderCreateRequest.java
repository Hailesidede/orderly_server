package com.marketplace.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderCreateRequest(

        @NotBlank(message = "Delivery location cannot be blank")
        String deliveryLocation,

        @NotBlank(message = "Delivery time slot cannot be blank")
        String deliveryTimeSlot,

        @NotEmpty(message = "Order must contain at least one item")
        List<OrderItemRequest> items
) {
}
