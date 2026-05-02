package com.reservation.application.payment.fixture

import com.reservation.application.payment.PaymentStrategy
import com.reservation.application.payment.command.PaymentCommand
import com.reservation.domain.payment.CancelResult
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException

class RecordingPaymentStrategy(
    override val method: PaymentMethod,
    private val events: MutableList<String>,
    private val payFailure: ErrorException? = null,
    private val cancelFailure: RuntimeException? = null,
) : PaymentStrategy {
    override fun pay(command: PaymentCommand): PaymentExecutionResult {
        events.add("pay:${method.name}")
        payFailure?.let { throw it }
        return PaymentExecutionResult(method = method, amount = command.amount, transactionId = "tx_${method.name}")
    }

    override fun cancel(transactionId: String): CancelResult {
        events.add("cancel:${method.name}:$transactionId")
        cancelFailure?.let { throw it }
        return CancelResult(method = method, transactionId = transactionId)
    }
}
