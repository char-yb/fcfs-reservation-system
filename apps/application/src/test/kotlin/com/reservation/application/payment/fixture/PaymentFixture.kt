package com.reservation.application.payment.fixture

import com.reservation.application.payment.PaymentService
import com.reservation.application.payment.PaymentStrategyRegistry
import com.reservation.application.payment.PaymentValidator
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType

data class PaymentServiceFixture(
    val service: PaymentService,
    val events: MutableList<String>,
)

fun paymentServiceFixture(
    events: MutableList<String> = mutableListOf(),
    failingMethod: PaymentMethod? = null,
): PaymentServiceFixture {
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
    return PaymentServiceFixture(
        service =
            PaymentService(
                strategyRegistry = PaymentStrategyRegistry(strategies),
                validator = PaymentValidator(),
            ),
        events = events,
    )
}

fun paymentCommand(
    method: PaymentMethod,
    amount: Long = 10_000,
): PaymentCommand =
    PaymentCommand(
        method = method,
        amount = amount,
        userId = 1L,
        attributes = emptyMap(),
    )
