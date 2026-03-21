package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.MerchantSummary;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.repositories.UserRepository;
import com.marketplace.backend.services.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/{merchantId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<Map<String, String>> subscribeToMerchant(
            @PathVariable UUID merchantId,
            @AuthenticationPrincipal User customer) {
        String message = subscriptionService.subscribeToMerchant(merchantId, customer.getId());
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/my-merchants")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<List<MerchantSummary>> getMyMerchants(@AuthenticationPrincipal User customer) {
        List<MerchantSummary> merchants = subscriptionService.getMyMerchants(customer.getId());
        return ResponseEntity.ok(merchants);
    }
}

