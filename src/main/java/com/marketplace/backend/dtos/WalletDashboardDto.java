package com.marketplace.backend.dtos;

import java.math.BigDecimal;
import java.util.List;

public record WalletDashboardDto(
        BigDecimal currentBalance,
        String currency,
        List<TransactionHistoryDto> recentTransactions
) {
}
