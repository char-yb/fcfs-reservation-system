package com.reservation.domain.payment

interface PaymentGateway {
    fun charge(request: PgChargeRequest): PgChargeResponse

    fun cancel(transactionId: String): PgCancelResponse
}
