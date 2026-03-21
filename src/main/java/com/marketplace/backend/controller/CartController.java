package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.CartAddItemRequest;
import com.marketplace.backend.dtos.CartSummaryResponse;
import com.marketplace.backend.dtos.CartUpdateQuantityRequest;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.services.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartSummaryResponse> getMyCart(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cartService.getCart(user.getId()));
    }

    @PostMapping("/items")
    public ResponseEntity<Void> addItemToCart(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CartAddItemRequest request) {

        cartService.addOrUpdateItem(user.getId(), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItemFromCart(
            @AuthenticationPrincipal User user,
            @PathVariable UUID productId) {

        cartService.removeItem(user.getId(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal User user) {
        cartService.clearCart(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<Void> updateItemQuantity(
            @AuthenticationPrincipal User user,
            @PathVariable UUID productId,
            @Valid @RequestBody CartUpdateQuantityRequest request) {

        cartService.updateItemQuantity(user.getId(), productId, request.quantity());
        return ResponseEntity.ok().build();
    }
}
