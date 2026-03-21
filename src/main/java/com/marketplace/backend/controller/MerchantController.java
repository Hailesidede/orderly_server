package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.MerchantProfileRequest;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.services.MerchantProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantProfileService profileService;

    @PostMapping("/profile")
    @PreAuthorize("hasAuthority('MERCHANT')")
    public ResponseEntity<Map<String, String>> setupProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MerchantProfileRequest request) {
        System.out.println("user loged in="+user);
        profileService.createProfile(user, request);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Store setup complete!"
        ));
    }
}
