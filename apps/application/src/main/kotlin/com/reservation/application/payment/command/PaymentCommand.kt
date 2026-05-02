package com.reservation.application.payment.command

import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.money.Money

data class PaymentCommand(
    val method: PaymentMethod,
    val amount: Money,
    val userId: Long,
    val attributes: Map<String, String> = emptyMap(),
) {
    constructor(
        method: PaymentMethod,
        amount: Long,
        userId: Long,
        attributes: Map<String, String> = emptyMap(),
    ) : this(
        method = method,
        amount = Money(amount),
        userId = userId,
        attributes = attributes,
    )
}
