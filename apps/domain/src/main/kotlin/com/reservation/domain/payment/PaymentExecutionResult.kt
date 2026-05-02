package com.reservation.domain.payment

import com.reservation.support.money.Money

data class PaymentExecutionResult(
    val method: PaymentMethod,
    val amount: Money,
    val transactionId: String,
) {
    constructor(
        method: PaymentMethod,
        amount: Long,
        transactionId: String,
    ) : this(
        method = method,
        amount = Money(amount),
        transactionId = transactionId,
    )
}
