package com.reservation.domain.payment

import com.reservation.support.money.Money

data class Payment(
    val id: Long,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: Money,
    val status: PaymentStatus,
    val pgTransactionId: String?,
    val externalRequestId: String?,
) {
    constructor(
        id: Long,
        orderId: Long,
        method: PaymentMethod,
        amount: Long,
        status: PaymentStatus,
        pgTransactionId: String?,
        externalRequestId: String?,
    ) : this(
        id = id,
        orderId = orderId,
        method = method,
        amount = Money(amount),
        status = status,
        pgTransactionId = pgTransactionId,
        externalRequestId = externalRequestId,
    )
}
