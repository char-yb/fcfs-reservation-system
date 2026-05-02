package com.reservation.application.fixture

import com.reservation.domain.payment.PaymentGateway
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.pg.PgCancelResponse
import com.reservation.domain.payment.pg.PgChargeRequest
import com.reservation.domain.payment.pg.PgChargeResponse
import com.reservation.support.error.ErrorException
import java.time.Instant

class RecordingPaymentGateway(
    override val method: PaymentMethod,
    private val chargeFailure: ErrorException? = null,
) : PaymentGateway {
    val chargeRequests = mutableListOf<PgChargeRequest>()
    val cancelledTransactionIds = mutableListOf<String>()

    override fun charge(request: PgChargeRequest): PgChargeResponse {
        chargeRequests.add(request)
        chargeFailure?.let { throw it }
        return PgChargeResponse(transactionId = "pg_${method.name}", approvedAt = Instant.now())
    }

    override fun cancel(transactionId: String): PgCancelResponse {
        cancelledTransactionIds.add(transactionId)
        return PgCancelResponse(cancelledAt = Instant.now())
    }
}
