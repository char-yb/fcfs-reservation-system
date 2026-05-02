package com.reservation.domain.payment

interface PaymentRepository {
    fun saveAll(payments: List<Payment>): List<Payment>

    fun findByOrderId(orderId: Long): List<Payment>

    fun updateStatusByOrderIdAndTransactionIds(
        orderId: Long,
        transactionIds: List<String>,
        status: PaymentStatus,
    ): Int
}
