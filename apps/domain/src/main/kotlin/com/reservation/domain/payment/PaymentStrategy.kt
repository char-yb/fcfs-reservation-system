package com.reservation.domain.payment

interface PaymentStrategy {
    val method: PaymentMethod

    fun pay(command: PaymentCommand): PaymentExecutionResult

    fun cancel(transactionId: String): CancelResult
}
