package com.reservation.application.payment

import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentValidator {
    fun validate(commands: List<PaymentCommand>) {
        if (commands.isEmpty() || commands.any { it.amount <= 0 }) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)

        val methods = commands.map { it.method }
        if (methods.size != methods.toSet().size) {
            throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
        }

        val hasCard = PaymentMethod.CREDIT_CARD in methods
        val hasYPay = PaymentMethod.Y_PAY in methods
        if (hasCard && hasYPay) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
    }

    fun validateTotal(
        commands: List<PaymentCommand>,
        expectedTotal: Long,
    ) {
        if (commands.sumOf { it.amount } != expectedTotal) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
    }
}
