package com.reservation.pg

import com.reservation.domain.payment.PaymentGateway
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.pg.PgCancelResponse
import com.reservation.domain.payment.pg.PgChargeRequest
import com.reservation.domain.payment.pg.PgChargeResponse
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** CreditCard Gateway Mock
 * 신용카드 결제 구현체로 실제 신용카드 결제 연동을 위한 클래스입니다. (ex. 포트원)
 * 실제 연동 시에는 API 호출 로직과 예외 처리를 구현해야 합니다.
 * 현재는 테스트용으로 사용되는 클래스입니다.
 */
@Component
class MockCreditCardGateway : PaymentGateway {
    override val method: PaymentMethod = PaymentMethod.CREDIT_CARD

    override fun charge(request: PgChargeRequest): PgChargeResponse {
        if (request.token.startsWith("exceeded_limit_")) {
            throw ErrorException(ErrorType.PAYMENT_DECLINED)
        }
        return PgChargeResponse(transactionId = "tx_${UUID.randomUUID()}", approvedAt = Instant.now())
    }

    override fun cancel(transactionId: String): PgCancelResponse = PgCancelResponse(cancelledAt = Instant.now())
}
