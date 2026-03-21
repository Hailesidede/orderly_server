package com.marketplace.backend.dtos;

import java.util.UUID;

public record OrderPaidEvent(UUID orderId) {
}
