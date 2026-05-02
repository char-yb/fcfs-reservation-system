package com.reservation.application.payment

import com.reservation.domain.payment.CancelResult
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentStrategy
import com.reservation.domain.payment.pg.PgChargeRequest
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType

abstract class PgPaymentStrategy(
    private val pgGatewayRegistry: PgGatewayRegistry,
    final override val method: PaymentMethod,
    private val pgMethod: String,
    private val tokenAttribute: String,
) : PaymentStrategy {
    override fun pay(command: PaymentCommand): PaymentExecutionResult {
        val token =
            command.attributes[tokenAttribute]
                ?: throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
        val response =
            pgGatewayRegistry.get(method).charge(
                PgChargeRequest(method = pgMethod, amount = command.amount, token = token),
            )
        return PaymentExecutionResult(method = method, amount = command.amount, transactionId = response.transactionId)
    }

    override fun cancel(transactionId: String): CancelResult {
        pgGatewayRegistry.get(method).cancel(transactionId)
        return CancelResult(method = method, transactionId = transactionId)
    }
}
