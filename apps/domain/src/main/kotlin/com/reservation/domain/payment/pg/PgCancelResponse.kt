package com.reservation.domain.payment.pg

import java.time.Instant

data class PgCancelResponse(
    val cancelledAt: Instant,
)
