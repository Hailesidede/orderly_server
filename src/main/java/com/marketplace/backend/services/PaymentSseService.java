package com.marketplace.backend.services; // FIX 1: Lowercase 'p'

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.configs.RedisPubSubConfig; // FIX 2: Imported the config class!
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor // FIX 3: Added the Lombok annotation so Spring injects your variables!
public class PaymentSseService {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SseEmitter subscribe(String checkoutRequestId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        emitters.put(checkoutRequestId, emitter);
        
        emitter.onCompletion(() -> emitters.remove(checkoutRequestId));
        emitter.onTimeout(() -> emitters.remove(checkoutRequestId));
        emitter.onError((e) -> emitters.remove(checkoutRequestId));
        
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected"));
        } catch (IOException e) {
            emitters.remove(checkoutRequestId);
        }
        return emitter;
    }

    public void publishPaymentResult(String checkoutRequestId, String status, String message) {
        try {
            Map<String, String> payload = Map.of(
                    "checkoutRequestId", checkoutRequestId,
                    "status", status,
                    "message", message
            );
            String json = objectMapper.writeValueAsString(payload);
            
            redisTemplate.convertAndSend(RedisPubSubConfig.PAYMENT_TOPIC, json);
            log.info("Published payment result to Redis for Checkout ID: {}", checkoutRequestId);
            
        } catch (Exception e) {
            log.error("Failed to publish to Redis for Checkout ID: {}", checkoutRequestId, e);
        }
    }

    public void notifyPaymentResultLocal(String checkoutRequestId, String status, String message) {
        SseEmitter emitter = emitters.get(checkoutRequestId);
        
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("PAYMENT_RESULT")
                        .data(Map.of("status", status, "message", message)));
                emitter.complete(); 
                log.info("Successfully pushed SSE event to client for Checkout ID: {}", checkoutRequestId);
            } catch (IOException e) {
                log.error("Failed to push SSE to client", e);
            } finally {
                emitters.remove(checkoutRequestId);
            }
        } 
    }
}
