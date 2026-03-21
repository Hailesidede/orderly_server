package com.marketplace.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkPushSyncResponse(
        @JsonProperty("MerchantRequestID") String merchantRequestId,
        @JsonProperty("CheckoutRequestID") String checkoutRequestId,
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("ResponseDescription") String responseDescription,
        @JsonProperty("CustomerMessage") String customerMessage
) {
}
