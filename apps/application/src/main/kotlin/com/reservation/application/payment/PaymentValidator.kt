package com.reservation.application.payment

import com.reservation.application.payment.command.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.money.Money
import org.springframework.stereotype.Component

@Component
class PaymentValidator {
    fun validate(commands: List<PaymentCommand>) {
        if (commands.isEmpty() || commands.any { !it.amount.isGreaterThan(Money.ZERO) }) {
            throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
        }

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
        expectedTotal: Money,
    ) {
        val actualTotal = commands.fold(Money.ZERO) { sum, command -> sum + command.amount }
        if (!actualTotal.isEqualsThan(expectedTotal)) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
    }
}
