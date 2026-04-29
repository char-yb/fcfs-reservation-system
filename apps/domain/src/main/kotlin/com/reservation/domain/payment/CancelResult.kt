package com.reservation.domain.payment

data class CancelResult(
    val method: PaymentMethod,
    val transactionId: String,
)
