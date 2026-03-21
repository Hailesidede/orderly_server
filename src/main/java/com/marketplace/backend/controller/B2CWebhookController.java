package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.B2CCallbackRequest;
import com.marketplace.backend.services.WithdrawalCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mpesa/b2c")
@RequiredArgsConstructor
@Slf4j
public class B2CWebhookController {

    private final WithdrawalCallbackService withdrawalCallbackService;

    @PostMapping("/result")
    public ResponseEntity<String> handleB2CResult(@RequestBody B2CCallbackRequest payload) {
        log.info("Received Daraja B2C Webhook...");

        withdrawalCallbackService.processB2CResult(payload);

        return ResponseEntity.ok("{\"ResultCode\": 0, \"ResultDesc\": \"Accepted\"}");
    }
}
