package com.reservation.domain.payment

data class PaymentExecutionResult(
    val method: PaymentMethod,
    val amount: Long,
    val transactionId: String,
)
