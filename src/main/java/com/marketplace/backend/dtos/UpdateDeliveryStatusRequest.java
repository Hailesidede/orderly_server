package com.marketplace.backend.dtos;

import com.marketplace.backend.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryStatusRequest(
        @NotNull(message = "New status is required") DeliveryStatus status,
        String pin
) {
}
