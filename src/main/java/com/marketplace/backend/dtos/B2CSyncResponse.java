package com.marketplace.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2CSyncResponse(
        @JsonProperty("ConversationID") String conversationID,
        @JsonProperty("OriginatorConversationID") String originatorConversationID,
        @JsonProperty("ResponseCode") String responseCode,
        @JsonProperty("ResponseDescription") String responseDescription
) {
}
