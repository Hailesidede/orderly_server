package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.MpesaCallbackResponse;
import com.marketplace.backend.dtos.StkPushInitiationRequest;
import com.marketplace.backend.dtos.StkPushSyncResponse;
import com.marketplace.backend.services.PaymentSseService;
import com.marketplace.backend.services.interfaces.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentSseService paymentSseService;

    @PostMapping("/stk-push")
    public ResponseEntity<Map<String, String>> initiateStkPush(@Valid @RequestBody StkPushInitiationRequest request) {

        StkPushSyncResponse safaricomResponse = paymentService.initiatePayment(request.orderId(), request.phoneNumber());

        if ("0".equals(safaricomResponse.responseCode())) {
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", safaricomResponse.customerMessage(),
                    "checkoutRequestId", safaricomResponse.checkoutRequestId()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "message", safaricomResponse.customerMessage() != null
                            ? safaricomResponse.customerMessage()
                            : safaricomResponse.responseDescription()
            ));
        }
    }

    @PostMapping("/mpesa/callback")
    public ResponseEntity<Map<String, String>> handleMpesaCallback(
            @RequestBody MpesaCallbackResponse callback) {

        log.info("Received M-Pesa Callback: {}", callback);


        // 2. Offload processing to avoid Safaricom timeouts
        // In a true enterprise setup, push this to a message queue (RabbitMQ/Kafka)
        // For this sprint, an async service method is acceptable.
        paymentService.processCallbackAsync(callback);

        // 3. Immediately return 200 OK to Safaricom
        return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Accepted"));
    }

    @GetMapping("/stream/{checkoutRequestId}")
    public SseEmitter streamPaymentStatus(@PathVariable String checkoutRequestId) {
        return paymentSseService.subscribe(checkoutRequestId);
    }
}
