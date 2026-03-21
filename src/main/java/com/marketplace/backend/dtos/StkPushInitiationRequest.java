package com.marketplace.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record StkPushInitiationRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(2547|2541|07|01|\\+2547|\\+2541)\\d{8}$", message = "Invalid Kenyan phone number format")
        String phoneNumber
) {
}
