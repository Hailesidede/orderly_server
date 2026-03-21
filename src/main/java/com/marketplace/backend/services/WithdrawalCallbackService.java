package com.marketplace.backend.services;

import com.marketplace.backend.dtos.B2CCallbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalCallbackService {

    private final LedgerService ledgerService;


    @Transactional
    public void processB2CResult(B2CCallbackRequest payload) {
        var result = payload.result();

        if (result == null) {
            log.error("Received malformed B2C webhook with null Result object.");
            return;
        }

        String conversationId = result.conversationID();
        // ResultCode 0 means Safaricom successfully delivered the cash.
        if (result.resultCode() != null && result.resultCode() == 0) {
            log.info("B2C SUCCESS. Daraja confirmed delivery. M-Pesa Receipt: {}", result.transactionID());
            ledgerService.confirmWithdrawalSuccess(conversationId, result.transactionID());
        } else {
            // FAILED! (This is where your 2040 error will land)
            log.error("B2C FAILED. Reversing Ledger. Reason: {} (Code: {})",
                    result.resultDesc(), result.resultCode());
            // Execute the self-healing ledger refund
            ledgerService.reverseFailedWithdrawal(conversationId, result.resultDesc());
        }
    }
}
