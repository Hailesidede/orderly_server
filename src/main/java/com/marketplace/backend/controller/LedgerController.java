package com.marketplace.backend.controller;

import com.marketplace.backend.entities.User;
import com.marketplace.backend.services.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/my-balance")
    @PreAuthorize("hasAnyAuthority('MERCHANT', 'DISTRIBUTOR')")
    public ResponseEntity<Map<String, BigDecimal>> getMyBalance(@AuthenticationPrincipal User user) {

        BigDecimal balance = ledgerService.getWalletBalance(user);
        return ResponseEntity.ok(Map.of("balance", balance));
    }
}
