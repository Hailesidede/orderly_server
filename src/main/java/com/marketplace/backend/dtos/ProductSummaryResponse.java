package com.marketplace.backend.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String name,
        String shortDesc,
        String categoryId,
        BigDecimal price,
        String imageUrl,
        String storeName
) {
}
