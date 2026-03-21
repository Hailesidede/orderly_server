package com.marketplace.backend.controller;

import com.marketplace.backend.dtos.ClaimDeliveryRequest;
import com.marketplace.backend.dtos.DeliveryResponse;
import com.marketplace.backend.dtos.UpdateDeliveryStatusRequest;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.services.DispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
@Slf4j
public class DeliveryController {

    private final DispatchService dispatchService;


    @GetMapping("/available")
    @PreAuthorize("hasAuthority('DISTRIBUTOR')")
    public ResponseEntity<List<DeliveryResponse>> getAvailableDeliveries(
            @AuthenticationPrincipal User distributor) {

        log.info("Fetching available deliveries for Distributor: {}", distributor.getId());

        List<DeliveryResponse> available = dispatchService.getAvailableDeliveries(distributor);
        return ResponseEntity.ok(available);
    }


    @PostMapping("/{deliveryId}/claim")
    public ResponseEntity<Map<String, String>> claimDelivery(
            @PathVariable UUID deliveryId,
            @AuthenticationPrincipal User distributor) {

        // The service handles the Pessimistic Locking to prevent double-booking
        dispatchService.claimDelivery(deliveryId, distributor.getId());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Delivery successfully claimed. Proceed to pickup."
        ));
    }

    @PatchMapping("/{deliveryId}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @PathVariable UUID deliveryId,
            @AuthenticationPrincipal User distributor,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {

        dispatchService.updateDeliveryStatus(deliveryId, distributor.getId(), request.status(), request.pin());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Delivery status updated to " + request.status()
        ));
    }

    @GetMapping("/order/{orderId}/track")
    public ResponseEntity<Map<String, Object>> trackDelivery(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User student) {

        Map<String, Object> trackingData = dispatchService.trackDeliveryByOrderId(orderId, student.getId());
        return ResponseEntity.ok(trackingData);
    }

    @GetMapping("/my-active")
    public ResponseEntity<List<DeliveryResponse>> getMyActiveDeliveries(
            @AuthenticationPrincipal User distributor) {

        // We fetch anything claimed or in transit by this specific driver
        List<DeliveryResponse> active = dispatchService.getMyActiveDeliveries(distributor.getId());
        return ResponseEntity.ok(active);
    }

//    @GetMapping("/available")
//    public ResponseEntity<List<DeliveryResponse>> getAvailableDeliveries() {
//        System.out.println("we have hit the endpoint");
//        List<DeliveryResponse> available = dispatchService.getAvailableDeliveries();
//        return ResponseEntity.ok(available);
//    }
}
