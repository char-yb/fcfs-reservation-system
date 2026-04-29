package com.reservation.domain.payment

data class PgChargeRequest(
    val method: String,
    val amount: Long,
    val token: String,
)
