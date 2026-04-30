package com.reservation.domain.payment

import com.reservation.domain.payment.pg.PgCancelResponse
import com.reservation.domain.payment.pg.PgChargeRequest
import com.reservation.domain.payment.pg.PgChargeResponse

interface PaymentGateway {
    val method: PaymentMethod

    fun charge(request: PgChargeRequest): PgChargeResponse

    fun cancel(transactionId: String): PgCancelResponse
}
