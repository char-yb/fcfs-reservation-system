package com.reservation.api.booking.request

import com.reservation.application.booking.BookingCommand
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentMethod
import jakarta.validation.constraints.Min

data class BookingRequest(
    val productId: Long,
    val totalAmount: Long,
    val payments: List<PaymentItem>,
) {
    data class PaymentItem(
        val method: PaymentMethod,
        @field:Min(1) val amount: Long,
        val attributes: Map<String, String> = emptyMap(),
    )

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
