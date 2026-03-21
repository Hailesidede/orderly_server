package com.marketplace.backend.dtos;

import java.math.BigDecimal;
import java.util.List;

public record CartSummaryResponse(
        List<CartItemResponse> items,
        BigDecimal cartTotal
) {
}
