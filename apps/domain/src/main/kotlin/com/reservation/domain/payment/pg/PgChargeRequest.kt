package com.reservation.domain.payment.pg

data class PgChargeRequest(
    val method: String,
    val amount: Long,
    val token: String,
)
