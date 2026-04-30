package com.reservation.domain.payment.pg

import java.time.Instant

data class PgChargeResponse(
    val transactionId: String,
    val approvedAt: Instant,
)
