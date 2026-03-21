package com.marketplace.backend.services;

import com.marketplace.backend.dtos.DeliveryResponse;
import com.marketplace.backend.entities.Delivery;
import com.marketplace.backend.entities.Order;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.enums.DeliveryStatus;
import com.marketplace.backend.enums.Role;
import com.marketplace.backend.repositories.DeliveryRepository;
import com.marketplace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchService {

    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;

     private final StringRedisTemplate redisTemplate;
     private final LedgerService ledgerService;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Delivery createDeliveryTicket(Order order) {
        System.out.println("order to be processed::"+order);

        String generatedPin = String.format("%04d", new Random().nextInt(10000));

          Delivery delivery = Delivery.builder()
                .order(order)
                .status(DeliveryStatus.UNASSIGNED)
                  .deliveryPin(generatedPin)
                .targetLocation(order.getDeliveryLocation())
                .timeSlot(order.getDeliveryTimeSlot())
                .build();

        log.info("Delivery ticket created for Order {}", order.getId());
        return deliveryRepository.save(delivery);
    }

    @Transactional
    public void claimDelivery(UUID deliveryId, UUID distributorId) {
        User distributor = userRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (distributor.getRole() != Role.DISTRIBUTOR) {
            throw new SecurityException("Only verified distributors can claim deliveries.");
        }

        Delivery delivery = deliveryRepository.findByIdWithPessimisticWriteLock(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery ticket not found."));

        if (delivery.getStatus() != DeliveryStatus.UNASSIGNED) {
            throw new IllegalStateException("Delivery has already been claimed or cancelled.");
        }

        delivery.setDistributor(distributor);
        delivery.setStatus(DeliveryStatus.CLAIMED);

        deliveryRepository.save(delivery);
        log.info("Distributor {} successfully claimed Delivery {}", distributorId, deliveryId);
    }


    @Transactional(readOnly = true)
    public List<DeliveryResponse> getAvailableDeliveries(User detachedDistributor) {

        User attachedDistributor = userRepository.findById(detachedDistributor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found."));
        List<UUID> employedMerchantIds = attachedDistributor.getSubscribedMerchants().stream()
                .map(User::getId)
                .toList();
        if (employedMerchantIds.isEmpty()) {
            return Collections.emptyList();
        }
        return deliveryRepository.findAvailableDeliveriesForMerchants(DeliveryStatus.UNASSIGNED, employedMerchantIds)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    @Transactional
    public void updateDeliveryStatus(UUID deliveryId, UUID distributorId, DeliveryStatus newStatus, String providedPin) {
        String lockKey = "delivery:update:lock:" + deliveryId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(5));

        if (Boolean.FALSE.equals(acquired)) {
            throw new IllegalStateException("System is currently processing this delivery. Please wait.");
        }

        try {
            Delivery delivery = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));

            if (delivery.getDistributor() == null || !delivery.getDistributor().getId().equals(distributorId)) {
                throw new SecurityException("You are not authorized to update this delivery.");
            }

            if (newStatus == DeliveryStatus.DELIVERED) {
                if (delivery.getStatus() != DeliveryStatus.CLAIMED && delivery.getStatus() != DeliveryStatus.IN_TRANSIT) {
                    throw new IllegalStateException("Invalid state transition.");
                }

                if (providedPin == null || !providedPin.equals(delivery.getDeliveryPin())) {
                    throw new IllegalArgumentException("Incorrect Delivery PIN. Ask the customer for the 4-digit code.");
                }

                ledgerService.recordDeliveryPayout(delivery);
                log.info("Financial payout triggered for Distributor {}", distributorId);
            }

            delivery.setStatus(newStatus);
            deliveryRepository.save(delivery);

        } finally {
            if (newStatus != DeliveryStatus.DELIVERED) {
                redisTemplate.delete(lockKey);
            }
        }
    }


    @Transactional(readOnly = true)
    public Map<String, Object> trackDeliveryByOrderId(UUID orderId, UUID studentId) {

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No delivery tracking available for this order yet."));

        // Security check: The student requesting the tracking MUST own the order
        if (!delivery.getOrder().getUser().getId().equals(studentId)) {
            throw new SecurityException("You do not have permission to track this order.");
        }

        String runnerName = null;
        if (delivery.getDistributor() != null) {
            runnerName = delivery.getDistributor().getFirstName();
        }

        return Map.of(
                "status", delivery.getStatus().name(),
                "targetLocation", delivery.getTargetLocation(),
                "deliveryPin", delivery.getDeliveryPin()

        );
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getMyActiveDeliveries(UUID distributorId) {
        return deliveryRepository.findByDistributorIdAndStatusIn(
                distributorId,
                List.of(DeliveryStatus.CLAIMED, DeliveryStatus.IN_TRANSIT)
        ).stream().map(this::mapToResponse).toList();
    }

    private DeliveryResponse mapToResponse(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrder().getId(),
                delivery.getTargetLocation(),
                delivery.getTimeSlot(),
                delivery.getStatus()
        );
    }

//    @Transactional
//    public void updateDeliveryStatus(UUID deliveryId, UUID distributorId, DeliveryStatus newStatus) {
//        String lockKey = "delivery:update:lock:" + deliveryId;
//        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(5));
//
//        if (Boolean.FALSE.equals(acquired)) {
//            log.warn("Double-tap blocked for Delivery {}. Update already in progress.", deliveryId);
//            throw new IllegalStateException("System is currently processing this delivery. Please wait.");
//        }
//
//        try {
//            Delivery delivery = deliveryRepository.findById(deliveryId)
//                    .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
//
//            if (delivery.getDistributor() == null || !delivery.getDistributor().getId().equals(distributorId)) {
//                throw new SecurityException("You are not authorized to update this delivery.");
//            }
//
//            if (delivery.getStatus() == newStatus) {
//                log.info("Delivery {} is already {}. Ignoring redundant update.", deliveryId, newStatus);
//                return;
//            }
//
//            if (newStatus == DeliveryStatus.DELIVERED) {
//                // You cannot deliver something that wasn't assigned or in transit
//                if (delivery.getStatus() != DeliveryStatus.CLAIMED && delivery.getStatus() != DeliveryStatus.IN_TRANSIT) {
//                    throw new IllegalStateException("Invalid state transition. Cannot mark as DELIVERED from " + delivery.getStatus());
//                }
//
//                ledgerService.recordDeliveryPayout(delivery);
//                log.info("Financial payout triggered for Distributor {}", distributorId);
//            }
//
//            delivery.setStatus(newStatus);
//            deliveryRepository.save(delivery);
//            log.info("Delivery {} status successfully updated to {} by Distributor {}", deliveryId, newStatus, distributorId);
//
//        } catch (Exception e) {
//            // If the database transaction fails (deadlock, constraint violation), release the lock immediately so they can retry.
//            redisTemplate.delete(lockKey);
//            throw e;
//        }
//
//        // Notice we do NOT delete the lock on success.
//        // We let the 5-second timer expire naturally to block the driver's phone from firing rapid automatic retries.
//    }


    //    @Transactional(readOnly = true)
//    public List<DeliveryResponse> getAvailableDeliveries() {
//        return deliveryRepository.findByStatus(DeliveryStatus.UNASSIGNED)
//                .stream()
//                .map(this::mapToResponse)
//                .toList();
//    }
}
