package com.marketplace.backend.repositories;

import com.marketplace.backend.entities.Delivery;
import com.marketplace.backend.enums.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    List<Delivery> findByStatus(DeliveryStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Delivery d WHERE d.id = :id")
    Optional<Delivery> findByIdWithPessimisticWriteLock(@Param("id") UUID id);

    Optional<Delivery> findByOrderId(UUID orderId);

    List<Delivery> findByDistributorIdAndStatusIn(UUID distributorId, List<DeliveryStatus> statuses);

    @Query("SELECT DISTINCT d FROM Delivery d " +
            "JOIN d.order o " +
            "JOIN o.items i " +
            "JOIN i.product p " +
            "WHERE d.status = :status " +
            "AND p.merchant.id IN :merchantIds")
    List<Delivery> findAvailableDeliveriesForMerchants(
            @Param("status") DeliveryStatus status,
            @Param("merchantIds") List<UUID> merchantIds
    );
}
