package com.marketplace.backend.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        DeliverySummary delivery
) {
}

