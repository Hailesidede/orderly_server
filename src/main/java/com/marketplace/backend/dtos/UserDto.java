package com.marketplace.backend.dtos;

import java.util.UUID;

public record UserDto(
        UUID id,
        String fullName,
        String email,
        String role
) {
}
