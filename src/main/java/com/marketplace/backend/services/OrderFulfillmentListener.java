package com.marketplace.backend.services;

import com.marketplace.backend.dtos.OrderPaidEvent;
import com.marketplace.backend.entities.Order;
import com.marketplace.backend.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderFulfillmentListener {

    private final OrderRepository orderRepository;
    private final DispatchService dispatchService;


    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        log.info("Fulfillment engine triggered for Order ID: {}", event.orderId());

        // 1. Re-fetch the order from the database
        Order order = orderRepository.findById(event.orderId()).orElse(null);

        if (order == null) {
            log.error("CRITICAL: Order {} vanished before fulfillment could start.", event.orderId());
            return;
        }

        try {
            // 2. Extract the dynamic location data the user provided during checkout
            String targetLocation = order.getDeliveryLocation();
            String timeSlot = order.getDeliveryTimeSlot();

            // 3. Create the Delivery Ticket
            // This decouples the financial Order from the physical Logistics.
            log.info("Creating UNASSIGNED delivery ticket for location: [{}] at [{}]", targetLocation, timeSlot);

             dispatchService.createDeliveryTicket(order);

            // 4. Notify active distributors (via WebSockets or Push Notification)
            // notificationService.broadcastNewDeliveryAvailable(targetLocation);

        } catch (Exception e) {
            // If the dispatch logic fails (e.g., database lock timeout on the delivery table),
            // we catch it here so it DOES NOT roll back the user's M-Pesa payment.
            log.error("Failed to generate delivery ticket for Order {}. Manual intervention required.", event.orderId(), e);
        }
    }
}
