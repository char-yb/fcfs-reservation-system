package com.reservation.api.booking.response

import com.reservation.application.booking.BookingResult
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentMethod

data class BookingResponse(
    val orderId: Long,
    val status: OrderStatus,
    val payments: List<PaymentInfo>,
) {
    data class PaymentInfo(
        val method: PaymentMethod,
        val amount: Long,
        val transactionId: String,
    )

    companion object {
        fun from(result: BookingResult): BookingResponse =
            BookingResponse(
                orderId = result.orderId,
                status = result.status,
                payments =
                    result.payments.map {
                        PaymentInfo(method = it.method, amount = it.amount, transactionId = it.transactionId)
                    },
            )
    }
}
