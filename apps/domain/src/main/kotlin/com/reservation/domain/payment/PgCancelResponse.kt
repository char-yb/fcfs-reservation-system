package com.reservation.domain.payment

import java.time.Instant

data class PgCancelResponse(
    val cancelledAt: Instant,
)
