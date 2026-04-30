package com.reservation.domain.payment

data class PaymentInfo(
    val method: PaymentMethod,
    val amount: Long,
    val transactionId: String
)
