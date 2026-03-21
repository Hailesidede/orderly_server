package com.marketplace.backend.services;

import com.marketplace.backend.dtos.MerchantSummary;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MerchantSummary> getMyMerchants(UUID customerId) {

        List<User> merchants = userRepository.findSubscribedMerchantsWithProfiles(customerId);

        return merchants.stream()
                .map(m -> {
                    String storeName = (m.getMerchantProfile() != null && m.getMerchantProfile().getStoreName() != null)
                            ? m.getMerchantProfile().getStoreName()
                            : m.getFirstName() + "'s Store";
                    return new MerchantSummary(m.getId(), storeName);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public String subscribeToMerchant(UUID merchantId, UUID customerId) {
        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found."));

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));

        customer.subscribeToMerchant(merchant);
        userRepository.save(customer);

        String storeName = (merchant.getMerchantProfile() != null)
                ? merchant.getMerchantProfile().getStoreName()
                : merchant.getFirstName();

        return "Successfully subscribed to " + storeName;
    }
}
