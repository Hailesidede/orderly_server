package com.marketplace.backend.repositories;

import com.marketplace.backend.entities.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findTop20ByAccountIdOrderByTransactionTimestampDesc(UUID accountId);

    @Query("SELECT le FROM LedgerEntry le WHERE le.transaction.referenceId = :receiptNumber AND le.account.name = :accountName")
    Optional<LedgerEntry> findByReceiptNumberAndAccountName(
            @Param("receiptNumber") String receiptNumber,
            @Param("accountName") String accountName
    );
}
