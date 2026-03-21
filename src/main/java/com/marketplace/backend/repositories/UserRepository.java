package com.marketplace.backend.repositories;

import com.marketplace.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    @Query("SELECT m FROM User c JOIN c.subscribedMerchants m LEFT JOIN FETCH m.merchantProfile WHERE c.id = :customerId")
    List<User> findSubscribedMerchantsWithProfiles(@Param("customerId") UUID customerId);
}
