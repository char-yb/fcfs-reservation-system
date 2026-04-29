package com.reservation.application.payment

import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentProcessor
import org.springframework.stereotype.Component

@Component
class PaymentProcessorRegistry(
    processors: List<PaymentProcessor>,
) {
    private val byMethod: Map<PaymentMethod, PaymentProcessor> =
        processors.associateBy { it.method }

    init {
        PaymentMethod.entries.forEach { method ->
            requireNotNull(byMethod[method]) {
                "PaymentProcessor implementation missing for $method"
            }
        }
    }

    fun get(method: PaymentMethod): PaymentProcessor = checkNotNull(byMethod[method]) { "지원하지 않는 결제 수단: $method" }
}
