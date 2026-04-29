package com.reservation.application.booking

import com.reservation.domain.order.OrderStatus
import com.reservation.domain.payment.PaymentExecutionResult

data class BookingResult(
    val orderId: Long,
    val status: OrderStatus,
    val payments: List<PaymentExecutionResult>,
)
