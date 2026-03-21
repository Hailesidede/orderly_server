package com.marketplace.backend.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String name,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subTotal,
        boolean isAvailable
) {
}
