package com.marketplace.backend.dtos;

import jakarta.validation.constraints.NotBlank;

public record MerchantProfileRequest(
        @NotBlank String storeName,
        @NotBlank String location,
        String description
) {
}
