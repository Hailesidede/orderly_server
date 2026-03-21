package com.marketplace.backend.repositories;

import com.marketplace.backend.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    // Spring Data JPA translates this into:
    // SELECT * FROM payments WHERE provider_transaction_id = ?
    Optional<Payment> findByProviderTransactionId(String providerTransactionId);
}
