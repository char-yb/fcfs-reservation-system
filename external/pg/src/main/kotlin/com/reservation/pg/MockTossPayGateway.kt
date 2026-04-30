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

/** TossPay Gateway Mock
 * 토스페이 구현체로 실제 토스페이 연동을 위한 클래스입니다.
 * 실제 연동 시에는 API 호출 로직과 예외 처리를 구현해야 합니다.
 * 현재는 테스트용으로 사용되는 클래스입니다.
 */
@Component
class MockTossPayGateway : PaymentGateway {
    override val method: PaymentMethod = PaymentMethod.TOSS_PAY

    override fun charge(request: PgChargeRequest): PgChargeResponse {
        if (request.token.startsWith("exceeded_limit_")) {
            throw ErrorException(ErrorType.PAYMENT_DECLINED)
        }
        return PgChargeResponse(transactionId = "tx_${UUID.randomUUID()}", approvedAt = Instant.now())
    }

    override fun cancel(transactionId: String): PgCancelResponse = PgCancelResponse(cancelledAt = Instant.now())
}
