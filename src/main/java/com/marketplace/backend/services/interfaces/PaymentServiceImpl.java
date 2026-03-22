package com.marketplace.backend.services.interfaces;

import com.marketplace.backend.dtos.MpesaCallbackResponse;
import com.marketplace.backend.dtos.OrderPaidEvent;
import com.marketplace.backend.dtos.StkPushSyncResponse;
import com.marketplace.backend.entities.Order;
import com.marketplace.backend.entities.OrderItem;
import com.marketplace.backend.entities.Payment;
import com.marketplace.backend.entities.Product;
import com.marketplace.backend.enums.OrderStatus;
import com.marketplace.backend.repositories.OrderRepository;
import com.marketplace.backend.repositories.PaymentRepository;
import com.marketplace.backend.services.LedgerService;
import com.marketplace.backend.services.MpesaService;
import com.marketplace.backend.services.PaymentSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;
    private final MpesaService mpesaService;
    // For decoupling side-effects (like notifying the dorm delivery driver)
    private final ApplicationEventPublisher eventPublisher;
    private final LedgerService ledgerService;
    private final PaymentSseService paymentSseService;

    @Lazy
    @Autowired
    private PaymentService selfProxy;

    private static final String IDEMPOTENCY_KEY_PREFIX = "mpesa:callback:lock:";

    @Override
    public StkPushSyncResponse initiatePayment(UUID orderId, String paymentPhoneNumber) {

        // 1. Fetch the Order and associated Payment
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        Payment payment = order.getPayment();
        if (payment == null || !"PENDING".equals(payment.getStatus())) {
            throw new IllegalStateException("Payment is not in a valid state for initiation.");
        }

        // 2. Make the HTTP Call to Safaricom
        String amountStr = String.valueOf(payment.getAmount().intValue()); // Safaricom expects integers without decimals

        StkPushSyncResponse response = mpesaService.initiateStkPush(paymentPhoneNumber, amountStr, orderId.toString());

        // 3. Handle Daraja Rejections (e.g., Invalid Phone Number)
        if (!"0".equals(response.responseCode())) {
            log.error("Safaricom rejected the STK Push request. Desc: {}", response.responseDescription());
            throw new RuntimeException("Payment initiation failed at gateway.");
        }

        // 4. Save the critical CheckoutRequestID to the database
        saveCheckoutRequestId(payment.getId(), response.checkoutRequestId());

        return response;
    }

    @Transactional
    protected void saveCheckoutRequestId(UUID paymentId, String checkoutRequestId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment record vanished."));

        payment.setProviderTransactionId(checkoutRequestId);
        paymentRepository.save(payment);
        log.info("Linked CheckoutRequestID {} to Payment {}", checkoutRequestId, paymentId);
    }

    @Override
    @Async
    public void processCallbackAsync(MpesaCallbackResponse callback) {
        var stkCallback = callback.body().stkCallback();
        String checkoutRequestId = stkCallback.checkoutRequestId();

        // 1. Redis Idempotency Lock (Atomic operation)
        String lockKey = IDEMPOTENCY_KEY_PREFIX + checkoutRequestId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofHours(24));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Duplicate callback detected for CheckoutRequestID: {}. Ignoring.", checkoutRequestId);
            return;
        }

        try {
            // Handoff to a strictly transactional method
            selfProxy.processTransactionalUpdate(stkCallback);
        } catch (Exception e) {
            // If the database transaction fails (e.g., deadlock), we must release the lock so a retry can succeed.
            redisTemplate.delete(lockKey);
            log.error("Failed to process M-Pesa callback for CheckoutRequestID: {}. Lock released.", checkoutRequestId, e);
            throw e;
        }

    }

    @Transactional
    @Override
    public void processTransactionalUpdate(MpesaCallbackResponse.StkCallback stkCallback) {
        String checkoutRequestId = stkCallback.checkoutRequestId();
        Integer resultCode = stkCallback.resultCode();

        // 2. Fetch the pending payment. If it's missing, your STK Push initiation code is broken.
        Payment payment = paymentRepository.findByProviderTransactionId(checkoutRequestId)
                .orElseThrow(() -> new IllegalStateException("No pending payment found for CheckoutRequestID: " + checkoutRequestId));

        if (!"PENDING".equals(payment.getStatus())) {
            log.info("Payment {} is already in status {}. Skipping.", payment.getId(), payment.getStatus());
            return;
        }

        Order order = payment.getOrder();

        // 3. Handle Failure States (Insufficient funds, cancelled, etc.)
        if (resultCode != 0) {
            log.info("Payment failed. ResultCode: {}, Desc: {}", resultCode, stkCallback.resultDesc());
            payment.setStatus("FAILED");
            order.setStatus(OrderStatus.PENDING);

            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                // productRepository.save(product) is not explicitly needed due to Hibernate dirty checking,
                // but ensure the Product entity is managed in this session.
            }

            paymentRepository.save(payment);
            orderRepository.save(order);
            paymentSseService.publishPaymentResult(checkoutRequestId, "FAILED", stkCallback.resultDesc());
            return;// Keep order pending so they can retry
        }

        // 4. Handle Success State
        List<MpesaCallbackResponse.MpesaItem> items = stkCallback.callbackMetadata().item();
        String mpesaReceiptNumber = extractMpesaItemValue(items, "MpesaReceiptNumber");
        String amountStr = extractMpesaItemValue(items, "Amount");

        if (amountStr == null || mpesaReceiptNumber == null) {
            throw new IllegalStateException("Malformed success payload: Missing amount or receipt number.");
        }

        BigDecimal paidAmount = new BigDecimal(amountStr);

        // 5. Security: Prevent Partial Payment Exploits
        if (paidAmount.compareTo(payment.getAmount()) < 0) {
            log.error("Partial payment detected! Expected: {}, Received: {}", payment.getAmount(), paidAmount);
            payment.setStatus("PARTIAL_PAYMENT_FRAUD");
            paymentRepository.save(payment);
            return;
        }

        // 6. Update Entities
        payment.setStatus("COMPLETED");
        payment.setProviderTransactionId(mpesaReceiptNumber); // Swap the CheckoutRequestID for the actual receipt
        order.setStatus(OrderStatus.PAID);

        paymentRepository.save(payment);
        orderRepository.save(order);


        ledgerService.recordMarketplaceSale(mpesaReceiptNumber, order, payment.getAmount());
        eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));

        paymentSseService.publishPaymentResult(checkoutRequestId, "SUCCESS", "Payment received successfully.");
        log.info("Successfully processed payment {} for Order {}", mpesaReceiptNumber, order.getId());
    }

    public String extractMpesaItemValue(List<MpesaCallbackResponse.MpesaItem> items, String key) {
        if (items == null) return null;
        return items.stream()
                .filter(item -> key.equals(item.name()))
                .map(item -> String.valueOf(item.value()))
                .findFirst()
                .orElse(null);
    }
}
