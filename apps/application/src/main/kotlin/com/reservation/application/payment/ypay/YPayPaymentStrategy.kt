package com.reservation.application.payment.ypay

import com.reservation.application.payment.PgGatewayRegistry
import com.reservation.application.payment.PgPaymentStrategy
import com.reservation.domain.payment.PaymentMethod
import org.springframework.stereotype.Component

@Component
class YPayPaymentStrategy(
    pgGatewayRegistry: PgGatewayRegistry,
) : PgPaymentStrategy(pgGatewayRegistry, PaymentMethod.Y_PAY, "Y_PAY", "payToken")
