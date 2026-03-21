package com.marketplace.backend.services;

import com.marketplace.backend.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaService {

    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient = RestClient.create();

    @Value("${safaricom.mpesa.stk.shortcode}")
    private String shortCode;

    @Value("${safaricom.mpesa.stk.passkey}")
    private String passkey;

    @Value("${safaricom.mpesa.c2b.result-url}")
    private String callbackUrl;

    @Value("${mpesa.base-url}")
    private String baseUrl;

    @Value("${mpesa.consumer-key}")
    private String consumerKey;

    @Value("${mpesa.consumer-secret}")
    private String consumerSecret;

    @Value("${safaricom.mpesa.b2c.shortcode}") // Adjust key if needed to match your properties file
    private String b2cShortcode;

    @Value("${safaricom.mpesa.b2c.initiator-name}")
    private String b2cInitiatorName;

    @Value("${safaricom.mpesa.b2c.security-credential}")
    private String b2cSecurityCredential;

    @Value("${safaricom.mpesa.b2c.result-url}")
    private String b2cResultUrl;

    @Value("${safaricom.mpesa.b2c.queue-timeout-url}")
    private String b2cTimeoutUrl;

    private static final String TOKEN_CACHE_KEY = "mpesa:oauth:token";


    public String getAccessToken() {
        // 1. Check Redis first. If it exists, return it instantly.
        String cachedToken = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
//        if (cachedToken != null) {
//            System.out.println("we are using a cached token here::"+cachedToken);
//            return cachedToken;
//        }

        // 2. If missing/expired, fetch a new one
        String credentials = consumerKey + ":" + consumerSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        log.info("Fetching new Daraja OAuth token...");
        DarajaTokenResponse response = restClient.get()
                .uri(baseUrl + "/oauth/v1/generate?grant_type=client_credentials")
                .header("Authorization", "Basic " + encodedCredentials)
                .retrieve()
                .body(DarajaTokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new RuntimeException("Failed to retrieve Daraja OAuth token.");
        }

        System.out.println("Access token from daraja::"+response.accessToken());

        // 3. Cache it in Redis for 55 minutes (Safaricom tokens expire in 60 minutes)
        redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, response.accessToken(), Duration.ofMinutes(5));

        return response.accessToken();
    }

    public StkPushSyncResponse initiateStkPush(String phoneNumber, String amount, String orderId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = generatePassword(shortCode, passkey, timestamp);

        String token = getAccessToken();

        StkPushRequest request = StkPushRequest.builder()
                .businessShortCode(shortCode)
                .password(password)
                .timestamp(timestamp)
                .transactionType("CustomerPayBillOnline")
                .amount(amount)
                .partyA(formatPhoneNumber(phoneNumber))
                .partyB(shortCode)
                .phoneNumber(formatPhoneNumber(phoneNumber))
                .callBackURL(callbackUrl)
                .accountReference(orderId)
                .transactionDesc("Marketplace Order Payment")
                .build();

        log.info("Initiating STK Push for Order: {}", orderId);

        return restClient.post()
                .uri(baseUrl + "/mpesa/stkpush/v1/processrequest")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(StkPushSyncResponse.class);
    }

    public String initiateB2C(String phoneNumber, java.math.BigDecimal amount) {

        String token = getAccessToken();
        String formattedPhone = formatPhoneNumber(phoneNumber);
        String amountStr = String.valueOf(amount.intValue()); // Safaricom strictly rejects decimals

        B2CRequest request = B2CRequest.builder()
                .initiatorName(b2cInitiatorName)
                .securityCredential(b2cSecurityCredential)
                .commandID("BusinessPayment") // Can also be 'SalaryPayment' or 'PromotionPayment'
                .amount(amountStr)
                .partyA(b2cShortcode)
                .partyB(formattedPhone)
                .remarks("Marketplace Withdrawal")
                .queueTimeOutURL(b2cTimeoutUrl)
                .resultURL(b2cResultUrl)
                .occassion("Payout")
                .build();

        log.info("Initiating B2C payout of {} KES to {} via Daraja", amountStr, formattedPhone);

        B2CSyncResponse response = restClient.post()
                .uri(baseUrl + "/mpesa/b2c/v1/paymentrequest")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(B2CSyncResponse.class);

        // Daraja uses "0" to indicate successful receipt of the payload
        if (response == null || !"0".equals(response.responseCode())) {
            log.error("Safaricom rejected the B2C request. Response: {}", response);
            throw new RuntimeException("Payment initiation failed at Safaricom gateway.");
        }

        log.info("B2C Request Accepted. ConversationID: {}", response.conversationID());

        // We return this ID to the WithdrawalService so it can be saved in the Ledger
        return response.conversationID();
    }

    private String generatePassword(String shortCode, String passkey, String timestamp) {
        String dataToEncode = shortCode + passkey + timestamp;
        return Base64.getEncoder().encodeToString(dataToEncode.getBytes());
    }

    private String formatPhoneNumber(String phone) {
        // Basic sanitizer: convert 07XX to 2547XX
        if (phone.startsWith("0")) {
            return "254" + phone.substring(1);
        }
        if (phone.startsWith("+254")) {
            return phone.substring(1);
        }
        return phone;
    }
}
