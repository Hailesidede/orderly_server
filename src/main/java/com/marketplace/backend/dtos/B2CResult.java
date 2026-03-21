package com.marketplace.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2CResult(
        @JsonProperty("ResultType") Integer resultType,
        @JsonProperty("ResultCode") Integer resultCode,
        @JsonProperty("ResultDesc") String resultDesc,
        @JsonProperty("OriginatorConversationID") String originatorConversationID,
        @JsonProperty("ConversationID") String conversationID,
        @JsonProperty("TransactionID") String transactionID
) {
}
