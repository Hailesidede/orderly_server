package com.marketplace.backend.services;

import com.marketplace.backend.dtos.DeliverySummary;
import com.marketplace.backend.dtos.OrderCreateRequest;
import com.marketplace.backend.dtos.OrderItemRequest;
import com.marketplace.backend.dtos.OrderSummaryResponse;
import com.marketplace.backend.entities.*;
import com.marketplace.backend.enums.OrderStatus;
import com.marketplace.backend.repositories.OrderRepository;
import com.marketplace.backend.repositories.ProductRepository;
import com.marketplace.backend.repositories.UserRepository;
import com.marketplace.backend.services.interfaces.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;


    @Override
    @Transactional
    public UUID processOrder(OrderCreateRequest request, UUID authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + authenticatedUserId));

        List<UUID> productIds = request.items().stream()
                .map(OrderItemRequest::productId)
                .sorted()
                .toList();

        // 3. Lock the exact rows in the database
        List<Product> products = productRepository.findAllByIdWithPessimisticWriteLock(productIds);

        if (products.size() != productIds.size()) {
            throw new IllegalArgumentException("One or more products in the cart are invalid or do not exist.");
        }

        // 4. Map for O(1) lookups during the loop
        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 5. Initialize the Order
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setDeliveryLocation(request.deliveryLocation());
        order.setDeliveryTimeSlot(request.deliveryTimeSlot());

        BigDecimal totalAmount = BigDecimal.ZERO;

        // 6. Process Items and modify inventory in-memory
        for (OrderItemRequest itemReq : request.items()) {
            Product product = productMap.get(itemReq.productId());

            if (product.getStockQuantity() < itemReq.quantity()) {
                throw new IllegalStateException("Insufficient stock for product: " + product.getName());
            }

            // Deduct stock immediately (Hibernate will auto-update the DB on commit)
            product.setStockQuantity(product.getStockQuantity() - itemReq.quantity());

            // Calculate item total
            BigDecimal itemTotal = product.getBasePrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            totalAmount = totalAmount.add(itemTotal);

            // Build OrderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemReq.quantity());
            orderItem.setPriceAtPurchase(product.getPrice());

            order.getItems().add(orderItem);
        }

        order.setTotalAmount(totalAmount);

        Payment payment = new Payment();
        payment.setAmount(totalAmount);
        payment.setProvider("M-PESA");
        payment.setStatus("PENDING");

        order.setPayment(payment);

        Order savedOrder = orderRepository.save(order);

        return savedOrder.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getMyOrders(UUID userId) {

        List<Order> myOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return myOrders.stream()
                .map(order -> new OrderSummaryResponse(
                        order.getId(),
                        order.getStatus().name(),
                        order.getTotalAmount(),
                        order.getCreatedAt(),
                        new DeliverySummary(order.getDeliveryLocation())
                ))
                .toList();
    }

}
