package com.marketplace.backend.services.interfaces;

import com.marketplace.backend.dtos.MpesaCallbackResponse;
import com.marketplace.backend.dtos.StkPushSyncResponse;

import java.util.UUID;

public interface PaymentService {
    void processCallbackAsync(MpesaCallbackResponse response);

    StkPushSyncResponse initiatePayment(UUID orderId, String phoneNumber);

    void processTransactionalUpdate(MpesaCallbackResponse.StkCallback stkCallback);
}
