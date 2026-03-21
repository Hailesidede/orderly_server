package com.marketplace.backend.services;

import com.marketplace.backend.dtos.MerchantProfileRequest;
import com.marketplace.backend.entities.MerchantProfile;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.repositories.MerchantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantProfileService {

    private final MerchantProfileRepository profileRepository;

    @Transactional
    public void createProfile(User owner, MerchantProfileRequest request) {

        // 1. Business Rule Validation
        if (profileRepository.findByOwner(owner).isPresent()) {
            log.warn("User {} attempted to create a duplicate store profile.", owner.getId());
            throw new IllegalStateException("Store profile already exists for this account.");
        }

        MerchantProfile profile = MerchantProfile.builder()
                .owner(owner)
                .storeName(request.storeName().trim())
                .location(request.location().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .build();

        profileRepository.save(profile);
        log.info("Successfully provisioned Merchant Profile '{}' for User {}", profile.getStoreName(), owner.getId());
    }
}
