package com.marketplace.backend.services;

import com.marketplace.backend.dtos.CartAddItemRequest;
import com.marketplace.backend.dtos.CartItemResponse;
import com.marketplace.backend.dtos.CartSummaryResponse;
import com.marketplace.backend.entities.Product;
import com.marketplace.backend.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;

    private static final String CART_KEY_PREFIX = "cart:user:";
    private static final Duration CART_TTL = Duration.ofDays(7);


    public void addOrUpdateItem(UUID userId, CartAddItemRequest request) {
        log.debug("User Id we got::" + userId);

        // 1. Fetch the new product and validate stock
        Product newProduct = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));

        if (newProduct.getStockQuantity() < request.quantity()) {
            throw new IllegalStateException("Insufficient stock for product: " + newProduct.getName());
        }

        String cartKey = CART_KEY_PREFIX + userId;
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        // 2. ENTERPRISE GUARD: Prevent mixing items from different merchants
        Set<String> existingProductIds = hashOps.keys(cartKey);
        if (existingProductIds != null && !existingProductIds.isEmpty()) {
            // Grab any existing product ID from the Redis cart
            String existingProductIdStr = existingProductIds.iterator().next();

            // If it's a different product, we must verify the merchants match
            if (!existingProductIdStr.equals(request.productId().toString())) {
                Product existingProduct = productRepository.findById(UUID.fromString(existingProductIdStr))
                        .orElseThrow(() -> new IllegalStateException("Cart contains invalid product"));

                if (!newProduct.getMerchant().getId().equals(existingProduct.getMerchant().getId())) {
                    throw new IllegalArgumentException("Your cart contains items from a different merchant. Please checkout or clear your cart first.");
                }
            }
        }

        // 3. Atomic increment. If key doesn't exist, Redis creates it.
        hashOps.increment(cartKey, request.productId().toString(), request.quantity());

        // 4. Reset the 7-day expiration timer on this cart
        redisTemplate.expire(cartKey, CART_TTL);

        log.debug("Updated cart for user {}, added {} of product {}", userId, request.quantity(), request.productId());
    }

    public CartSummaryResponse getCart(UUID userId) {
        String cartKey = CART_KEY_PREFIX + userId;
        Map<Object, Object> redisCart = redisTemplate.opsForHash().entries(cartKey);

        if (redisCart.isEmpty()) {
            return new CartSummaryResponse(Collections.emptyList(), BigDecimal.ZERO);
        }

        Map<UUID, Integer> cartMap = new HashMap<>();
        redisCart.forEach((k, v) -> cartMap.put(UUID.fromString((String) k), Integer.parseInt((String) v)));

        List<Product> currentProducts = productRepository.findAllById(cartMap.keySet());

        BigDecimal cartTotal = BigDecimal.ZERO;
        List<CartItemResponse> items = new ArrayList<>();

        for (Product product : currentProducts) {
            int requestedQty = cartMap.get(product.getId());
            boolean isAvailable = product.getStockQuantity() >= requestedQty;

            int effectiveQty = isAvailable ? requestedQty : product.getStockQuantity();

            BigDecimal unitPrice = product.getBasePrice();
            BigDecimal subTotal = unitPrice.multiply(BigDecimal.valueOf(effectiveQty));

            if (isAvailable) {
                cartTotal = cartTotal.add(subTotal);
            }

            items.add(new CartItemResponse(
                    product.getId(),
                    product.getName(),
                    product.getImageUrl(),
                    requestedQty,
                    unitPrice,
                    subTotal,
                    isAvailable
            ));
        }

        return new CartSummaryResponse(items, cartTotal);
    }

    public void removeItem(UUID userId, UUID productId) {
        String cartKey = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().delete(cartKey, productId.toString());
    }

    public void clearCart(UUID userId) {
        redisTemplate.delete(CART_KEY_PREFIX + userId);
    }

    public void updateItemQuantity(UUID userId, UUID productId, int newQuantity) {
        // 1. Edge Case Protection: If someone bypasses frontend validation and sends 0 or negative.
        if (newQuantity <= 0) {
            removeItem(userId, productId);
            return;
        }

        // 2. Validate against current PostgreSQL truth
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));

        if (product.getStockQuantity() < newQuantity) {
            throw new IllegalStateException("Only " + product.getStockQuantity() + " items left in stock for " + product.getName());
        }

        String cartKey = CART_KEY_PREFIX + userId;

        // 3. HSET overrides the existing hash field with the new absolute string value
        redisTemplate.opsForHash().put(cartKey, productId.toString(), String.valueOf(newQuantity));

        // 4. Always reset the eviction timer on activity
        redisTemplate.expire(cartKey, CART_TTL);

        log.info("User {} updated product {} quantity to exact value {}", userId, productId, newQuantity);
    }
}
