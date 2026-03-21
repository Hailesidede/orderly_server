package com.marketplace.backend.dtos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank(message = "Product name cannot be empty")
        String name,

        @NotBlank(message = "Product description cannot be empty")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
        BigDecimal price,

        @NotNull @PositiveOrZero BigDecimal shadowDeliveryFee,

        @NotNull(message = "Initial stock quantity is required")
        @Min(value = 0, message = "Stock cannot be negative")
        Integer stockQuantity
) {
}
