package com.marketplace.backend.dtos;

import com.marketplace.backend.enums.DeliveryStatus;

import java.util.UUID;

public record DeliveryResponse(
        UUID deliveryId,
        UUID orderId, // So the driver can cross-reference the physical receipt
        String targetLocation,
        String timeSlot,
        DeliveryStatus status
) {
}
