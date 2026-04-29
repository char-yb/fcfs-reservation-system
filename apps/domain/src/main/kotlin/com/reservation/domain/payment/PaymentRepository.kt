package com.reservation.domain.payment

interface PaymentRepository {
    fun saveAll(payments: List<Payment>): List<Payment>

    fun findByOrderId(orderId: Long): List<Payment>
}
