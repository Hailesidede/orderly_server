package com.marketplace.backend.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ClaimDeliveryRequest(
        @NotNull(message = "Distributor ID is required") UUID distributorId
) {
}
