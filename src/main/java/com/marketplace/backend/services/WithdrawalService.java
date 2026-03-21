package com.marketplace.backend.services;

import com.marketplace.backend.entities.Account;
import com.marketplace.backend.entities.LedgerEntry;
import com.marketplace.backend.entities.User;
import com.marketplace.backend.enums.EntryType;
import com.marketplace.backend.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final MpesaService mpesaService; // Your Safaricom Integration Service
    private final StringRedisTemplate redisTemplate;


    @Transactional
    public void processWithdrawal(User user, BigDecimal requestedAmount) {

        // --- TIER 1: THE IDEMPOTENCY SHIELD ---
        // Block the user from making another withdrawal request for 30 seconds
        String lockKey = "withdrawal:lock:" + user.getId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(30));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Double-tap withdrawal blocked for User: {}", user.getId());
            throw new IllegalStateException("A withdrawal is already processing. Please wait 30 seconds.");
        }

        try {
            // STAGE 2: THE OPTIMISTIC DEBIT (Pessimistic Lock)
            Account userAccount = accountRepository.findByOwnerIdWithPessimisticWriteLock(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Wallet not found."));

            if (userAccount.getBalance().compareTo(requestedAmount) < 0) {
                throw new IllegalStateException("Insufficient funds. Your balance is: " + userAccount.getBalance());
            }

            Account pendingAccount = accountRepository.findByName("PENDING_WITHDRAWALS")
                    .orElseThrow(() -> new IllegalStateException("System Account missing: PENDING_WITHDRAWALS"));

            // STAGE 3: THE SAFARICOM HANDSHAKE
            // This is a synchronous HTTP call. If Safaricom is down, this throws an exception,
            // the database rolls back, and the driver loses nothing.
            String conversationId = mpesaService.initiateB2C(user.getPhoneNumber(), requestedAmount);

            // STAGE 2 CONTINUED: THE LEDGER SPLIT
            // Safaricom accepted the request. Move money from Driver to Transit.
            List<LedgerEntry> entries = List.of(
                    LedgerEntry.builder().account(userAccount).amount(requestedAmount).type(EntryType.DEBIT).build(),
                    LedgerEntry.builder().account(pendingAccount).amount(requestedAmount).type(EntryType.CREDIT).build()
            );

            // Save using the Safaricom Conversation ID as the receipt!
           ledgerService.processAndSaveLedger(entries, conversationId, "Pending Daraja B2C Transfer");

            log.info("Successfully locked {} KES in Transit for User {}. Daraja Ref: {}",
                    requestedAmount, user.getId(), conversationId);

        } catch (Exception e) {
            // If anything fails, drop the Redis lock immediately so they can try again
            redisTemplate.delete(lockKey);
            throw e;
        }
    }
}
