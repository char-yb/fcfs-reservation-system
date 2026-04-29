package com.reservation.domain.payment

data class Payment(
    val id: Long,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: Long,
    val status: PaymentStatus,
    val pgTransactionId: String?,
    val externalRequestId: String?,
)
