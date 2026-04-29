package com.reservation.domain.payment

data class PaymentCommand(
    val method: PaymentMethod,
    val amount: Long,
    val attributes: Map<String, String> = emptyMap(),
)
