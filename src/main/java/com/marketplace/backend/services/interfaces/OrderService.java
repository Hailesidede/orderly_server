package com.marketplace.backend.services.interfaces;

import com.marketplace.backend.dtos.OrderCreateRequest;
import com.marketplace.backend.dtos.OrderSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    UUID processOrder(OrderCreateRequest request, UUID authenticatedUserId);

    List<OrderSummaryResponse> getMyOrders(UUID userId);
}
