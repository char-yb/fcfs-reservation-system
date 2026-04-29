package com.reservation.application.payment

import com.reservation.domain.payment.CancelResult
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentGateway
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentProcessor
import com.reservation.domain.payment.PgChargeRequest
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class YPayProcessor(
    private val pgClient: PaymentGateway,
) : PaymentProcessor {
    override val method: PaymentMethod = PaymentMethod.PAY

    override fun pay(command: PaymentCommand): PaymentExecutionResult {
        val payToken =
            command.attributes["payToken"]
                ?: throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)

        val response = pgClient.charge(PgChargeRequest(method = "PAY", amount = command.amount, token = payToken))
        return PaymentExecutionResult(method = method, amount = command.amount, transactionId = response.transactionId)
    }

    override fun cancel(transactionId: String): CancelResult {
        pgClient.cancel(transactionId)
        return CancelResult(method = method, transactionId = transactionId)
    }
}
