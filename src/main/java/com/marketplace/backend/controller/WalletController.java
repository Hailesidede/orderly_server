package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.WalletDashboardDto;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.services.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<WalletDashboardDto> getMyWallet(@AuthenticationPrincipal User authenticatedUser) {
        WalletDashboardDto dashboard = walletService.getMyWalletDashboard(authenticatedUser.getId());
        return ResponseEntity.ok(dashboard);
    }
}
