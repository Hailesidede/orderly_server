package com.marketplace.backend.repositories;

import com.marketplace.backend.entities.MerchantProfile;
import com.marketplace.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, UUID> {

    Optional<MerchantProfile> findByOwner(User owner);
}
