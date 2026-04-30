package com.reservation.application.payment.fixture

import com.reservation.application.payment.PaymentService
import com.reservation.application.payment.PaymentStrategyRegistry
import com.reservation.application.payment.PaymentValidator
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType

internal object PaymentTestFixture {
    fun paymentService(
        events: MutableList<String>,
        failingMethod: PaymentMethod? = null,
    ): PaymentService {
        val strategies =
            PaymentMethod.entries.map { method ->
                RecordingPaymentStrategy(
                    method = method,
                    events = events,
                    payFailure =
                        if (method == failingMethod) {
                            ErrorException(ErrorType.PAYMENT_DECLINED)
                        } else {
                            null
                        },
                )
            }
        return PaymentService(
            strategyRegistry = PaymentStrategyRegistry(strategies),
            validator = PaymentValidator(),
        )
    }

    fun command(
        method: PaymentMethod,
        amount: Long = 10_000,
    ): PaymentCommand =
        PaymentCommand(
            method = method,
            amount = amount,
            userId = 1L,
            attributes = emptyMap(),
        )
}
