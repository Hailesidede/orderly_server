package com.marketplace.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MpesaCallbackResponse(
        @JsonProperty("Body") Body body
) {
    public record Body(
            @JsonProperty("stkCallback") StkCallback stkCallback
    ) {}

    public record StkCallback(
            @JsonProperty("MerchantRequestID") String merchantRequestId,
            @JsonProperty("CheckoutRequestID") String checkoutRequestId,
            @JsonProperty("ResultCode") Integer resultCode,
            @JsonProperty("ResultDesc") String resultDesc,
            @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata
    ) {}

    public record CallbackMetadata(
            @JsonProperty("Item") List<MpesaItem> item
    ) {}

    public record MpesaItem(
            @JsonProperty("Name") String name,
            @JsonProperty("Value") Object value
    ) {}
}
