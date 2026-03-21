package com.marketplace.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2CCallbackRequest(
        @JsonProperty("Result") B2CResult result
) {
}
