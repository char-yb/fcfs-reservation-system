package com.reservation.application.payment.credit

import com.reservation.application.payment.PgGatewayRegistry
import com.reservation.application.payment.PgPaymentStrategy
import com.reservation.domain.payment.PaymentMethod
import org.springframework.stereotype.Component

@Component
class CreditCardPaymentStrategy(
    pgGatewayRegistry: PgGatewayRegistry,
) : PgPaymentStrategy(pgGatewayRegistry, PaymentMethod.CREDIT_CARD, "CARD", "cardToken")
