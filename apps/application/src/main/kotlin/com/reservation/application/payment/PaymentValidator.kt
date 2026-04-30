package com.reservation.application.payment

import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentValidator {
    fun validate(commands: List<PaymentCommand>) {
        require(commands.isNotEmpty()) { "결제 수단이 비어있습니다" }

        val types = commands.map { it.method }
        if (types.size != types.toSet().size) {
            throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
        }

        val hasCard = PaymentMethod.CREDIT_CARD in types
        val hasPgPay = types.any { it == PaymentMethod.TOSS_PAY || it == PaymentMethod.KAKAO_PAY }
        if (hasCard && hasPgPay) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)

        commands.forEach {
            if (it.amount <= 0) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
        }
    }

    fun validateTotal(
        commands: List<PaymentCommand>,
        expectedTotal: Long,
    ) {
        val sum = commands.sumOf { it.amount }
        if (sum != expectedTotal) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
    }
}
