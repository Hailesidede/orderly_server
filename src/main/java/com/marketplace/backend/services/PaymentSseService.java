package com.marketplace.backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PaymentSseService {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String checkoutRequestId) {
        // 10-minute timeout. Outlasts Safaricom's internal STK push timeout.
        SseEmitter emitter = new SseEmitter(600_000L);
        emitters.put(checkoutRequestId, emitter);

        emitter.onCompletion(() -> emitters.remove(checkoutRequestId));
        emitter.onTimeout(() -> emitters.remove(checkoutRequestId));
        emitter.onError((e) -> emitters.remove(checkoutRequestId));

        // Send a dummy INIT event to immediately flush the response headers
        // and keep reverse proxies (like Nginx) from dropping the connection.
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected"));
        } catch (IOException e) {
            emitters.remove(checkoutRequestId);
        }

        return emitter;
    }

    public void notifyPaymentResult(String checkoutRequestId, String status, String message) {
        SseEmitter emitter = emitters.get(checkoutRequestId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("PAYMENT_RESULT")
                        .data(Map.of("status", status, "message", message)));
                emitter.complete(); // Close the connection gracefully
            } catch (IOException e) {
                log.error("Failed to push SSE to client for CheckoutRequestID: {}", checkoutRequestId);
                emitters.remove(checkoutRequestId);
            }
        } else {
            // This happens if the user closed their browser tab before entering their PIN
            log.warn("No active SSE connection found for CheckoutRequestID: {}", checkoutRequestId);
        }
    }
}
