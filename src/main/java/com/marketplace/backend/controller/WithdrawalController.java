package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.WithdrawalRequestDto;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.services.WithdrawalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping("/withdraw")
    public ResponseEntity<String> requestWithdrawal(
            @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody WithdrawalRequestDto request) {

        withdrawalService.processWithdrawal(authenticatedUser, request.amount());

        return ResponseEntity.ok("Withdrawal initiated successfully.");
    }

}
