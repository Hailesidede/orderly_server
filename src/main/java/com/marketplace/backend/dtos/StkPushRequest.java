package com.marketplace.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StkPushRequest {
    @JsonProperty("BusinessShortCode")
    private String businessShortCode;

    @JsonProperty("Password")
    private String password;

    @JsonProperty("Timestamp")
    private String timestamp; // Format: YYYYMMDDHHmmss

    @JsonProperty("TransactionType")
    private String transactionType; // "CustomerPayBillOnline" or "CustomerBuyGoodsOnline"

    @JsonProperty("Amount")
    private String amount;

    @JsonProperty("PartyA")
    private String partyA; // The customer's phone number (254...)

    @JsonProperty("PartyB")
    private String partyB; // Your PayBill or Till Number

    @JsonProperty("PhoneNumber")
    private String phoneNumber; // The customer's phone number again

    @JsonProperty("CallBackURL")
    private String callBackURL; // Your Spring Boot webhook endpoint

    @JsonProperty("AccountReference")
    private String accountReference; // e.g., The Order UUID

    @JsonProperty("TransactionDesc")
    private String transactionDesc;
}
