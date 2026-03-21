package com.marketplace.backend.dtos;

public record AuthenticationResponse(
        String accessToken,
        UserDto user
) {
}
