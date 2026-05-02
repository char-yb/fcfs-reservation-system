package com.reservation.domain.outbox

import com.reservation.domain.payment.PaymentMethod

data class CompensationFailurePayload(
    val orderId: Long,
    val method: PaymentMethod,
    val amount: Long,
    val pgTransactionId: String,
    val reason: String,
) : OutboxEventPayload
