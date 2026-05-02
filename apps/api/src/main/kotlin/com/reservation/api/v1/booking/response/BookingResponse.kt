package com.reservation.api.v1.booking.response

import com.reservation.application.booking.result.BookingResult
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentMethod
import java.math.BigDecimal

data class BookingResponse(
    val orderId: Long,
    val status: OrderStatus,
    val payments: List<BookingPaymentResponse>,
) {
    companion object {
        fun from(result: BookingResult): BookingResponse =
            BookingResponse(
                orderId = result.orderId,
                status = result.status,
                payments =
                    result.payments.map {
                        BookingPaymentResponse(
                            method = it.method,
                            amount = it.amount.amount,
                            transactionId = it.transactionId,
                        )
                    },
            )
    }
}

data class BookingPaymentResponse(
    val method: PaymentMethod,
    val amount: BigDecimal,
    val transactionId: String,
)
