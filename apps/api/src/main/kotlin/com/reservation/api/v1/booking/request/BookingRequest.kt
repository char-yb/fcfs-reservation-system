package com.reservation.api.v1.booking.request

import com.reservation.application.booking.command.BookingCommand
import com.reservation.application.payment.command.PaymentCommand
import com.reservation.support.money.Money
import java.math.BigDecimal

data class BookingRequest(
    val productOptionId: Long,
    val totalAmount: BigDecimal,
    val payments: List<PaymentRequestItem>,
) {
    fun toCommand(
        userId: Long,
        orderKey: String,
    ): BookingCommand =
        BookingCommand(
            productOptionId = productOptionId,
            userId = userId,
            totalAmount = Money(totalAmount),
            orderKey = orderKey,
            payments =
                payments.map {
                    PaymentCommand(
                        method = it.method,
                        amount = Money(it.amount),
                        userId = userId,
                        attributes = it.attributes,
                    )
                },
        )
}
