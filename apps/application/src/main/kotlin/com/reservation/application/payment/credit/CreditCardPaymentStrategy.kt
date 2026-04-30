package com.reservation.application.payment.credit

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
class CreditCardPaymentStrategy(
    private val pgGatewayRegistry: PgGatewayRegistry,
) : PaymentStrategy {
    override val method: PaymentMethod = PaymentMethod.CREDIT_CARD

    override fun pay(command: PaymentCommand): PaymentExecutionResult {
        val cardToken =
            command.attributes["cardToken"]
                ?: throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)

        val response =
            pgGatewayRegistry.get(method).charge(
                PgChargeRequest(
                    method = "CARD",
                    amount = command.amount,
                    token = cardToken,
                ),
            )
        return PaymentExecutionResult(method = method, amount = command.amount, transactionId = response.transactionId)
    }

    override fun cancel(transactionId: String): CancelResult {
        pgGatewayRegistry.get(method).cancel(transactionId)
        return CancelResult(method = method, transactionId = transactionId)
    }
}
