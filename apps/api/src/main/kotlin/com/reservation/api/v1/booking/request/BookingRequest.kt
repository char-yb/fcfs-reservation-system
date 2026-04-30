package com.reservation.api.v1.booking.request

import com.reservation.application.booking.command.BookingCommand
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentItem

data class BookingRequest(
    val productId: Long,
    val totalAmount: Long,
    val payments: List<PaymentItem>,
) {
    fun toCommand(
        userId: Long,
        orderKey: String,
    ): BookingCommand =
        BookingCommand(
            productId = productId,
            userId = userId,
            totalAmount = totalAmount,
            orderKey = orderKey,
            payments = payments.map { PaymentCommand(method = it.method, amount = it.amount, attributes = it.attributes) },
        )
}
