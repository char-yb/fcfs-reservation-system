package com.reservation.application.payment.kakao

import com.reservation.application.payment.PgGatewayRegistry
import com.reservation.domain.payment.CancelResult
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentStrategy
import com.reservation.domain.payment.pg.PgChargeRequest
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class KakaoPayPaymentStrategy(
    private val pgGatewayRegistry: PgGatewayRegistry,
) : PaymentStrategy {
    override val method: PaymentMethod = PaymentMethod.KAKAO_PAY

    override fun pay(command: PaymentCommand): PaymentExecutionResult {
        val payToken =
            command.attributes["payToken"]
                ?: throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)

        val response = pgGatewayRegistry.get(method).charge(
            PgChargeRequest(
                method = "KAKAO_PAY",
                amount = command.amount,
                token = payToken
            )
        )
        return PaymentExecutionResult(method = method, amount = command.amount, transactionId = response.transactionId)
    }

    override fun cancel(transactionId: String): CancelResult {
        pgGatewayRegistry.get(method).cancel(transactionId)
        return CancelResult(method = method, transactionId = transactionId)
    }
}
