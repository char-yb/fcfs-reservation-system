package com.reservation.application.payment

import com.reservation.application.user.UserPointService
import com.reservation.domain.payment.CancelResult
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentStrategy
import org.springframework.stereotype.Component

@Component
class YPointPaymentStrategy(
    private val userPointService: UserPointService,
) : PaymentStrategy {
    override val method: PaymentMethod = PaymentMethod.Y_POINT

    override fun pay(command: PaymentCommand): PaymentExecutionResult {
        val txId = userPointService.deduct(command.userId, command.amount)
        return PaymentExecutionResult(method = method, amount = command.amount, transactionId = txId)
    }

    override fun cancel(transactionId: String): CancelResult {
        userPointService.refund(transactionId)
        return CancelResult(method = method, transactionId = transactionId)
    }
}
