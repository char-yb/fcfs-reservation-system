package com.reservation.domain.payment.pg

import com.reservation.support.money.Money

data class PgChargeRequest(
    val method: String,
    val amount: Money,
    val token: String,
)
