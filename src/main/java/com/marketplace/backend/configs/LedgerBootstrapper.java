package com.marketplace.backend.configs;

import com.marketplace.backend.entities.Account;
import com.marketplace.backend.enums.AccountType;
import com.marketplace.backend.repositories.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerBootstrapper implements CommandLineRunner {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Inspecting system ledger infrastructure...");

        initializeSystemAccount("MPESA_ESCROW", AccountType.ASSET);
        initializeSystemAccount("PLATFORM_REVENUE", AccountType.EQUITY);
        initializeSystemAccount("UNALLOCATED_DELIVERY_POOL", AccountType.LIABILITY);
        initializeSystemAccount("PENDING_WITHDRAWALS", AccountType.LIABILITY);

        log.info("System ledger infrastructure verified.");
    }


    private void initializeSystemAccount(String accountName, AccountType type) {
        if (accountRepository.findByName(accountName).isEmpty()) {
            Account systemAccount = Account.builder()
                    .name(accountName)
                    .type(type)
                    .balance(BigDecimal.ZERO)
                    .owner(null)
                    .build();
            accountRepository.save(systemAccount);
            log.info("Bootstrapped missing system account: {}", accountName);
        }
    }
}
