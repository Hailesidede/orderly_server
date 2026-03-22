package com.marketplace.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisPaymentSubscriber {

    @Bean
public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
    return new com.fasterxml.jackson.databind.ObjectMapper();
}

    private final PaymentSseService sseService;
    private final ObjectMapper objectMapper;

    public void handleMessage(String message, String channel) {
        try {
            JsonNode payload = objectMapper.readTree(message);
            String checkoutRequestId = payload.get("checkoutRequestId").asText();
            String status = payload.get("status").asText();
            String textMessage = payload.get("message").asText();

            sseService.notifyPaymentResultLocal(checkoutRequestId, status, textMessage);

        } catch (Exception e) {
            log.error("Error processing Redis Pub/Sub message: {}", e.getMessage());
        }
    }
}
