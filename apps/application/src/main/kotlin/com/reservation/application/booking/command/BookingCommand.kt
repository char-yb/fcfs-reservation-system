package com.reservation.application.booking.command

import com.reservation.application.payment.command.PaymentCommand
import com.reservation.support.money.Money

data class BookingCommand(
    val productOptionId: Long,
    val userId: Long,
    val totalAmount: Money,
    val orderKey: String,
    val payments: List<PaymentCommand>,
) {
    constructor(
        productOptionId: Long,
        userId: Long,
        totalAmount: Long,
        orderKey: String,
        payments: List<PaymentCommand>,
    ) : this(
        productOptionId = productOptionId,
        userId = userId,
        totalAmount = Money(totalAmount),
        orderKey = orderKey,
        payments = payments,
    )
}
