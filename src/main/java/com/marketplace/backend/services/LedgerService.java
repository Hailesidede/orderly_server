package com.marketplace.backend.services;

import com.marketplace.backend.entities.*;
import com.marketplace.backend.enums.EntryType;
import com.marketplace.backend.repositories.AccountRepository;
import com.marketplace.backend.repositories.LedgerEntryRepository;
import com.marketplace.backend.repositories.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {
    private final LedgerTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Value("${marketplace.fees.platform-percentage}")
    private BigDecimal platformFeePercentage;

    @Value("${marketplace.fees.delivery-percentage}")
    private BigDecimal deliveryFeePercentage;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMarketplaceSale(String mpesaReceipt, Order order, BigDecimal mpesaTotalAmount) {

        BigDecimal totalDeliveryPool = BigDecimal.ZERO;
        BigDecimal totalPlatformCommission = BigDecimal.ZERO;
        BigDecimal totalMerchantPayout = BigDecimal.ZERO;

        // 1. DYNAMIC MATH: Loop through the cart and calculate fees per item
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            BigDecimal qty = new BigDecimal(item.getQuantity());

            // Extract the driver's money first
            BigDecimal itemDeliveryFee = product.getShadowDeliveryFee().multiply(qty);
            System.out.println("item deliveryFee is ::"+itemDeliveryFee);

            // The actual value of the good (e.g., 40 KES for the bread)
            BigDecimal itemBasePrice = (product.getBasePrice().multiply(qty)).subtract(itemDeliveryFee);
            System.out.println("item base price is ::"+itemBasePrice);

            // The platform takes its cut from the BASE price, not the delivery fee
            BigDecimal itemCommission = itemBasePrice.multiply(platformFeePercentage).setScale(4, RoundingMode.HALF_UP);
            BigDecimal itemMerchantPayout = itemBasePrice.subtract(itemCommission);

            // Accumulate totals for the ledger entries
            totalDeliveryPool = totalDeliveryPool.add(itemDeliveryFee);
            totalPlatformCommission = totalPlatformCommission.add(itemCommission);
            totalMerchantPayout = totalMerchantPayout.add(itemMerchantPayout);
        }

        // 2. THE VAULT CHECK: Does the math equal the actual money Safaricom sent us?
        BigDecimal expectedTotal = totalDeliveryPool.add(totalPlatformCommission).add(totalMerchantPayout);
          //BigDecimal expectedTotal = totalPlatformCommission.add(totalMerchantPayout);
        if (expectedTotal.compareTo(mpesaTotalAmount) != 0) {
            throw new IllegalStateException(
                    String.format("FATAL: Order math mismatch. M-Pesa sent %s, but Ledger calculated %s",
                            mpesaTotalAmount, expectedTotal)
            );
        }

        // 3. Fetch System Accounts (Exactly as you had them before)
        Account mpesaEscrow = accountRepository.findByName("MPESA_ESCROW")
                .orElseThrow(() -> new IllegalStateException("System Account missing"));
        Account platformRevenue = accountRepository.findByName("PLATFORM_REVENUE")
                .orElseThrow(() -> new IllegalStateException("System Account missing"));
        Account deliveryPool = accountRepository.findByName("UNALLOCATED_DELIVERY_POOL")
                .orElseThrow(() -> new IllegalStateException("System Account missing"));

        User merchant = order.getItems().get(0).getProduct().getMerchant();
        Account merchantAccount = accountRepository.findByOwner(merchant)
                .orElseThrow(() -> new IllegalStateException("Merchant account missing"));

        // 4. Build the Immutable Entries
        List<LedgerEntry> entries = List.of(
                LedgerEntry.builder().account(mpesaEscrow).amount(mpesaTotalAmount).type(EntryType.DEBIT).build(),
                LedgerEntry.builder().account(platformRevenue).amount(totalPlatformCommission).type(EntryType.CREDIT).build(),
                LedgerEntry.builder().account(deliveryPool).amount(totalDeliveryPool).type(EntryType.CREDIT).build(),
                LedgerEntry.builder().account(merchantAccount).amount(totalMerchantPayout).type(EntryType.CREDIT).build()
        );

        // 5. Execute secure save (Your existing helper method)
        processAndSaveLedger(entries, mpesaReceipt, "Marketplace Sale - Order ID: " + order.getId());
    }


    // --- EVENT 1: CALLED BY THE M-PESA WEBHOOK ---
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void recordMarketplaceSale(String mpesaReceipt, Order order, BigDecimal totalAmount) {
//
//        // 1. Calculate dynamic fees securely (Scale 4 for extreme precision, rounded to nearest even)
//        BigDecimal platformFee = totalAmount.multiply(platformFeePercentage).setScale(4, RoundingMode.HALF_UP);
//        BigDecimal deliveryFee = totalAmount.multiply(deliveryFeePercentage).setScale(4, RoundingMode.HALF_UP);
//
//        // Merchant gets whatever is left over so we never leak pennies due to rounding
//        BigDecimal merchantPayout = totalAmount.subtract(platformFee).subtract(deliveryFee);
//
//        // 2. Fetch System Accounts
//        Account mpesaEscrow = accountRepository.findByName("MPESA_ESCROW")
//                .orElseThrow(() -> new IllegalStateException("System Account MPESA_ESCROW missing"));
//        Account platformRevenue = accountRepository.findByName("PLATFORM_REVENUE")
//                .orElseThrow(() -> new IllegalStateException("System Account PLATFORM_REVENUE missing"));
//        Account deliveryPool = accountRepository.findByName("UNALLOCATED_DELIVERY_POOL")
//                .orElseThrow(() -> new IllegalStateException("System Account UNALLOCATED_DELIVERY_POOL missing"));
//
//        // 3. Fetch the SPECIFIC Merchant's Account
//        // Note: Assumes order.getItems() is not empty and all items belong to the same merchant
//        User merchant = order.getItems().get(0).getProduct().getMerchant();
//
//        if (merchant == null) {
//            throw new IllegalStateException("FATAL: Product does not have an assigned Merchant owner.");
//        }
//
//        Account merchantAccount = accountRepository.findByOwner(merchant)
//                .orElseThrow(() -> new IllegalStateException("Merchant account missing for user: " + merchant.getId()));
//
//        // 4. Build Entries
//        List<LedgerEntry> entries = List.of(
//                LedgerEntry.builder().account(mpesaEscrow).amount(totalAmount).type(EntryType.DEBIT).build(),
//                LedgerEntry.builder().account(platformRevenue).amount(platformFee).type(EntryType.CREDIT).build(),
//                LedgerEntry.builder().account(deliveryPool).amount(deliveryFee).type(EntryType.CREDIT).build(),
//                LedgerEntry.builder().account(merchantAccount).amount(merchantPayout).type(EntryType.CREDIT).build()
//        );
//
//        // 5. Execute core ledger logic
//        processAndSaveLedger(entries, mpesaReceipt, "Marketplace Sale - Order ID: " + order.getId());
//    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeliveryPayout(Delivery delivery) {

        // 1. DYNAMIC MATH: Recalculate the exact driver payout directly from the Order Items
        BigDecimal totalDriverPayout = BigDecimal.ZERO;

        for (OrderItem item : delivery.getOrder().getItems()) {
            Product product = item.getProduct();
            BigDecimal qty = new BigDecimal(item.getQuantity());

            // Extract the hidden driver fee for this specific item and multiply by quantity
            BigDecimal itemDeliveryFee = product.getShadowDeliveryFee().multiply(qty);
            totalDriverPayout = totalDriverPayout.add(itemDeliveryFee);
        }

        // The Sanity Check: If the delivery is worth 0 KES, don't waste database cycles
        if (totalDriverPayout.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Delivery {} has a calculated payout of 0.00 KES. Skipping ledger entry.", delivery.getId());
            return;
        }

        // 2. Fetch Accounts
        Account deliveryPool = accountRepository.findByName("UNALLOCATED_DELIVERY_POOL")
                .orElseThrow(() -> new IllegalStateException("System Account UNALLOCATED_DELIVERY_POOL missing"));

        User distributor = delivery.getDistributor();
        if (distributor == null) {
            throw new IllegalStateException("FATAL: Delivery does not have an assigned Distributor.");
        }

        Account distributorAccount = accountRepository.findByOwner(distributor)
                .orElseThrow(() -> new IllegalStateException("Distributor account missing for user: " + distributor.getId()));

        // 3. Move the exact calculated amount out of the pool and into the driver's wallet
        List<LedgerEntry> entries = List.of(
                LedgerEntry.builder().account(deliveryPool).amount(totalDriverPayout).type(EntryType.DEBIT).build(),
                LedgerEntry.builder().account(distributorAccount).amount(totalDriverPayout).type(EntryType.CREDIT).build()
        );

        // 4. Execute core ledger logic
        processAndSaveLedger(entries, "DELIVERY_" + delivery.getId().toString(), "Payout for Delivery ID: " + delivery.getId());
    }


//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void recordDeliveryPayout(Delivery delivery) {
//
//        // 1. Dynamically recalculate the exact fee based on the Order total and configuration
//        BigDecimal totalOrderAmount = delivery.getOrder().getTotalAmount();
//        BigDecimal deliveryFee = totalOrderAmount.multiply(deliveryFeePercentage).setScale(4, RoundingMode.HALF_UP);
//
//        // 2. Fetch Accounts
//        Account deliveryPool = accountRepository.findByName("UNALLOCATED_DELIVERY_POOL")
//                .orElseThrow(() -> new IllegalStateException("System Account UNALLOCATED_DELIVERY_POOL missing"));
//
//        User distributor = delivery.getDistributor();
//        if (distributor == null) {
//            throw new IllegalStateException("FATAL: Delivery does not have an assigned Distributor.");
//        }
//
//        Account distributorAccount = accountRepository.findByOwner(distributor)
//                .orElseThrow(() -> new IllegalStateException("Distributor account missing for user: " + distributor.getId()));
//
//        // 3. Move the money out of the pool and into the driver's wallet
//        List<LedgerEntry> entries = List.of(
//                LedgerEntry.builder().account(deliveryPool).amount(deliveryFee).type(EntryType.DEBIT).build(),
//                LedgerEntry.builder().account(distributorAccount).amount(deliveryFee).type(EntryType.CREDIT).build()
//        );
//
//        // 4. Execute core ledger logic
//        processAndSaveLedger(entries, "DELIVERY_" + delivery.getId().toString(), "Payout for Delivery ID: " + delivery.getId());
//    }

    public void processAndSaveLedger(List<LedgerEntry> entries, String referenceId, String description) {
        // A. Verify Accounting Equation (Total Debits == Total Credits)
        verifyDoubleEntryBalance(entries);

        // B. Build the Transaction
        LedgerTransaction transaction = LedgerTransaction.builder()
                .referenceId(referenceId)
                .description(description)
                .timestamp(Instant.now())
                .entries(entries)
                .build();

        // C. Execute the secure balance updates (Handles Deadlock Prevention & Locking)
        processAccountBalances(entries);

        // D. Save Immutable Audit Trail to Database
        entries.forEach(e -> e.setTransaction(transaction));
        transactionRepository.save(transaction);

        log.info("Ledger transaction [{}] recorded successfully.", referenceId);
    }

    private void verifyDoubleEntryBalance(List<LedgerEntry> entries) {
        BigDecimal debits = entries.stream()
                .filter(e -> e.getType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal credits = entries.stream()
                .filter(e -> e.getType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException(String.format("FATAL: Ledger imbalance. Debits: %s, Credits: %s", debits, credits));
        }
    }

    private void processAccountBalances(List<LedgerEntry> entries) {
        // Step A: Extract unique account IDs and SORT THEM to prevent Postgres Deadlocks
        List<UUID> accountIds = entries.stream()
                .map(e -> e.getAccount().getId())
                .distinct()
                .sorted()
                .toList();

        // Step B: Apply Pessimistic Write Lock to the sorted rows
        List<Account> lockedAccounts = accountRepository.findAllByIdsWithPessimisticWriteLock(accountIds);

        if (lockedAccounts.size() != accountIds.size()) {
            throw new IllegalStateException("Failed to acquire locks on all required ledger accounts.");
        }

        // Step C: Map for O(1) lookup
        Map<UUID, Account> accountMap = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getId, a -> a));

        // Step D: Apply the math
        for (LedgerEntry entry : entries) {
            Account lockedAccount = accountMap.get(entry.getAccount().getId());
            lockedAccount.applyEntry(entry.getType(), entry.getAmount());
            // Hibernate's dirty checking will automatically issue the UPDATE statements
            // for these accounts when the transaction commits.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmWithdrawalSuccess(String conversationId, String mpesaReceipt) {

        // 1. Find the 100 KES locked in transit
        LedgerEntry pendingCredit = ledgerEntryRepository.findByReceiptNumberAndAccountName(conversationId, "PENDING_WITHDRAWALS")
                .orElseThrow(() -> new IllegalStateException("Cannot find pending withdrawal for Daraja ID: " + conversationId));

        BigDecimal withdrawalAmount = pendingCredit.getAmount();
        Account pendingAccount = pendingCredit.getAccount();

        Account escrowAccount = accountRepository.findByName("MPESA_ESCROW")
                .orElseThrow(() -> new IllegalStateException("System Account missing: MPESA_ESCROW"));

        // 2. The Final Clear: Debit Transit, Credit Escrow (Cash officially leaves the system)
        List<LedgerEntry> clearingEntries = List.of(
                LedgerEntry.builder().account(pendingAccount).amount(withdrawalAmount).type(EntryType.DEBIT).build(),
                LedgerEntry.builder().account(escrowAccount).amount(withdrawalAmount).type(EntryType.CREDIT).build()
        );

        // Save the final movement, stamping the actual Safaricom Receipt onto it
        processAndSaveLedger(clearingEntries, mpesaReceipt, "B2C Cleared | Orig Ref: " + conversationId);

        log.info("Successfully cleared {} KES to Escrow. Daraja Receipt: {}", withdrawalAmount, mpesaReceipt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reverseFailedWithdrawal(String conversationId, String failureReason) {

        // 1. Find the 100 KES locked in transit
        LedgerEntry pendingCredit = ledgerEntryRepository.findByReceiptNumberAndAccountName(conversationId, "PENDING_WITHDRAWALS")
                .orElseThrow(() -> new IllegalStateException("Cannot find pending withdrawal for Daraja ID: " + conversationId));

        BigDecimal withdrawalAmount = pendingCredit.getAmount();
        Account pendingAccount = pendingCredit.getAccount();

        // 2. Find the original driver to refund. We look at the other side of the original transaction.
        LedgerEntry originalUserDebit = pendingCredit.getTransaction().getEntries().stream()
                .filter(e -> e.getType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Corrupted transaction: No user debit found to refund."));

        Account userAccount = originalUserDebit.getAccount();

        // 3. The Refund: Debit Transit, Credit User (Money goes back to their wallet)
        List<LedgerEntry> refundEntries = List.of(
                LedgerEntry.builder().account(pendingAccount).amount(withdrawalAmount).type(EntryType.DEBIT).build(),
                LedgerEntry.builder().account(userAccount).amount(withdrawalAmount).type(EntryType.CREDIT).build()
        );

        processAndSaveLedger(refundEntries, "REFUND_" + conversationId, "B2C Failed: " + failureReason);

        log.warn("Reversed {} KES back to User {} due to B2C Failure: {}", withdrawalAmount, userAccount.getId(), failureReason);
    }

    @Transactional(readOnly = true)
    public BigDecimal getWalletBalance(User user) {
        Account userAccount = accountRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalStateException("Ledger account missing for user. Please contact support."));

        return userAccount.getBalance();
    }
}
