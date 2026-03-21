package com.marketplace.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthenticationRequest(
        @NotBlank String phoneNumber,
        @NotBlank String password,
        String referralMerchantId
) {
}
