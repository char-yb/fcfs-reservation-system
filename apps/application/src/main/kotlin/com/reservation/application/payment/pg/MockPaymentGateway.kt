package com.reservation.application.payment.pg

import com.reservation.domain.payment.PaymentGateway
import com.reservation.domain.payment.PgCancelResponse
import com.reservation.domain.payment.PgChargeRequest
import com.reservation.domain.payment.PgChargeResponse
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Profile("!prod")
@Component
class MockPaymentGateway : PaymentGateway {
    override fun charge(request: PgChargeRequest): PgChargeResponse {
        if (request.token.startsWith("exceeded_limit_")) {
            throw ErrorException(ErrorType.PAYMENT_DECLINED)
        }
        return PgChargeResponse(
            transactionId = "tx_${UUID.randomUUID()}",
            approvedAt = Instant.now(),
        )
    }

    override fun cancel(transactionId: String): PgCancelResponse = PgCancelResponse(cancelledAt = Instant.now())
}
