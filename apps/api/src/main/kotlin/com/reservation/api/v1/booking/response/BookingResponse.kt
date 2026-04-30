package com.reservation.api.v1.booking.response

import com.reservation.application.booking.result.BookingResult
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentInfo

data class BookingResponse(
    val orderId: Long,
    val status: OrderStatus,
    val payments: List<PaymentInfo>,
) {
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
