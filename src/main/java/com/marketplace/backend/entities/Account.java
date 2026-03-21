package com.marketplace.backend.entities;

import com.marketplace.backend.enums.AccountType;
import com.marketplace.backend.enums.EntryType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name; // e.g., "MPESA_ESCROW", "REVENUE_FEES", "MERCHANT_PAYABLE_UUID"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE

    @Column(nullable = false)
    private BigDecimal balance; // Must be updated ONLY via ledger entrie

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    public void applyEntry(EntryType entryType, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be strictly positive.");
        }

        if (this.type == AccountType.ASSET || this.type == AccountType.EXPENSE) {
            // Assets and Expenses increase with Debits
            this.balance = (entryType == EntryType.DEBIT)
                    ? this.balance.add(amount)
                    : this.balance.subtract(amount);
        } else {
            // Liabilities, Equity, and Revenue increase with Credits
            this.balance = (entryType == EntryType.CREDIT)
                    ? this.balance.add(amount)
                    : this.balance.subtract(amount);
        }
    }
}
