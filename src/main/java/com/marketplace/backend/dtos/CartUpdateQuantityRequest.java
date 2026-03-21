package com.marketplace.backend.dtos;

import jakarta.validation.constraints.Min;

public record CartUpdateQuantityRequest(
        @Min(value = 1, message = "Quantity must be at least 1. To remove, use DELETE.")
        int quantity
) {
}
