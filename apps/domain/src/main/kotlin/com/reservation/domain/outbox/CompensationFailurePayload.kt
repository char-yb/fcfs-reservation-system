package com.reservation.domain.outbox

import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.money.Money

data class CompensationFailurePayload(
    val orderId: Long,
    val method: PaymentMethod,
    val amount: Money,
    val pgTransactionId: String,
    val reason: String,
) : OutboxEventPayload {
    constructor(
        orderId: Long,
        method: PaymentMethod,
        amount: Number,
        pgTransactionId: String,
        reason: String,
    ) : this(
        orderId = orderId,
        method = method,
        amount =
            when (amount) {
                is Int -> Money(amount)
                is Long -> Money(amount)
                is Double -> Money(amount)
                else -> Money(amount.toLong())
            },
        pgTransactionId = pgTransactionId,
        reason = reason,
    )
}
