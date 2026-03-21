package com.marketplace.backend.dtos;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryDto(
        String description,
        BigDecimal amount,
        String type, // CREDIT or DEBIT
        Instant timestamp
) {
}
