package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.OrderCreateRequest;
import com.marketplace.backend.dtos.OrderSummaryResponse;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.services.DispatchService;
import com.marketplace.backend.services.interfaces.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final DispatchService dispatchService;

    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderCreateRequest orderCreateRequest,@AuthenticationPrincipal User authenticatedUser) {
        UUID orderId = orderService.processOrder(orderCreateRequest, authenticatedUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("orderId", orderId));
    }

    @GetMapping("/{orderId}/track")
    public ResponseEntity<Map<String, Object>> trackDelivery(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User student) {

        Map<String, Object> trackingData = dispatchService.trackDeliveryByOrderId(orderId, student.getId());
        return ResponseEntity.ok(trackingData);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<List<OrderSummaryResponse>> getMyOrders(@AuthenticationPrincipal User authenticatedUser) {

        List<OrderSummaryResponse> myOrders = orderService.getMyOrders(authenticatedUser.getId());

        return ResponseEntity.ok(myOrders);
    }
}
