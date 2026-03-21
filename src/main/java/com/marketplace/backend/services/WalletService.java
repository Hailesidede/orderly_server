package com.marketplace.backend.services;

import com.marketplace.backend.dtos.TransactionHistoryDto;
import com.marketplace.backend.dtos.WalletDashboardDto;
import com.marketplace.backend.entities.Account;
import com.marketplace.backend.entities.LedgerEntry;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.repositories.AccountRepository;
import com.marketplace.backend.repositories.LedgerEntryRepository;
import com.marketplace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public WalletDashboardDto getMyWalletDashboard(UUID authenticatedUserId) {

        User user = userRepository.findById(authenticatedUserId).orElse(null);

        Account account = accountRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalStateException("No wallet account found for this user."));

        // 2. Fetch the immutable audit trail (Last 20 transactions)
        List<LedgerEntry> recentEntries = ledgerEntryRepository.findTop20ByAccountIdOrderByTransactionTimestampDesc(account.getId());

        // 3. Map the database entities to the clean DTO for Vue.js
        List<TransactionHistoryDto> history = recentEntries.stream()
                .map(entry -> new TransactionHistoryDto(
                        entry.getTransaction().getDescription(), // e.g., "Payout for Delivery ID: 123"
                        entry.getAmount(),
                        entry.getType().name(), // "CREDIT" or "DEBIT"
                        entry.getTransaction().getTimestamp()
                ))
                .toList();

        log.info("Wallet dashboard generated for User {}. Balance: {}", authenticatedUserId, account.getBalance());

        // 4. Return the complete package
        return new WalletDashboardDto(account.getBalance(), "KES", history);
    }
}
