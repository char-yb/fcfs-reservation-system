package com.reservation.application.booking

import com.reservation.domain.payment.PaymentCommand

data class BookingCommand(
    val productId: Long,
    val userId: Long,
    val totalAmount: Long,
    val orderKey: String,
    val payments: List<PaymentCommand>,
)
