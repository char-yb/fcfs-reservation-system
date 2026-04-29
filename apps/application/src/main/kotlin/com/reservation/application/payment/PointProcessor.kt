package com.reservation.application.payment

import com.reservation.application.user.UserPointService
import com.reservation.domain.payment.CancelResult
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentProcessor
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PointProcessor(
    private val userPointService: UserPointService,
) : PaymentProcessor {
    override val method: PaymentMethod = PaymentMethod.POINT

    override fun pay(command: PaymentCommand): PaymentExecutionResult {
        val userId =
            command.attributes["userId"]?.toLongOrNull()
                ?: throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)

        val txId = userPointService.deduct(userId, command.amount)
        return PaymentExecutionResult(method = method, amount = command.amount, transactionId = txId)
    }

    override fun cancel(transactionId: String): CancelResult {
        userPointService.refund(transactionId)
        return CancelResult(method = method, transactionId = transactionId)
    }
}
