package com.marketplace.backend.dtos;

import java.util.UUID;

public record MerchantSummary(
        UUID id,
        String name
) {
}
