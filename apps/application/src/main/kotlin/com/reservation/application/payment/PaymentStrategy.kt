package com.reservation.application.payment

import com.reservation.application.payment.command.PaymentCommand
import com.reservation.domain.payment.CancelResult
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod

interface PaymentStrategy {
    val method: PaymentMethod

    fun pay(command: PaymentCommand): PaymentExecutionResult

    fun cancel(transactionId: String): CancelResult
}
