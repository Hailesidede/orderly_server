package com.marketplace.backend.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record MerchantProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String imageUrl
) {
}
