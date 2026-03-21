package com.marketplace.backend.repositories;

import com.marketplace.backend.entities.Account;
import com.marketplace.backend.entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByName(String name);

    Optional<Account> findByOwner(User owner);

    // THE ARCHITECT'S LOCK
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids")
    List<Account> findAllByIdsWithPessimisticWriteLock(@Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.owner.id = :ownerId")
    Optional<Account> findByOwnerIdWithPessimisticWriteLock(@Param("ownerId") UUID ownerId);
}
