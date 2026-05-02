package com.reservation.application.fixture

import com.reservation.domain.payment.Payment
import com.reservation.domain.payment.PaymentRepository
import com.reservation.domain.payment.PaymentStatus

class FakePaymentRepository(
    initialPayments: List<Payment> = emptyList(),
    private val saveFailure: RuntimeException? = null,
    private val events: MutableList<String> = mutableListOf(),
) : PaymentRepository {
    val payments: MutableList<Payment> = initialPayments.toMutableList()
    private var nextId = (payments.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun saveAll(payments: List<Payment>): List<Payment> {
        saveFailure?.let { throw it }
        val saved =
            payments.map { payment ->
                if (payment.id == 0L) {
                    payment.copy(id = nextId++)
                } else {
                    payment
                }
            }
        this.payments.addAll(saved)
        saved.forEach { events.add("payment:${it.status.name}:${it.method.name}") }
        return saved
    }

    override fun findByOrderId(orderId: Long): List<Payment> = payments.filter { it.orderId == orderId }

    override fun updateStatusByOrderIdAndTransactionIds(
        orderId: Long,
        transactionIds: List<String>,
        status: PaymentStatus,
    ): Int {
        var updated = 0
        payments.replaceAll { payment ->
            if (payment.orderId == orderId && payment.pgTransactionId in transactionIds) {
                updated += 1
                events.add("payment:${status.name}:${payment.method.name}")
                payment.copy(status = status)
            } else {
                payment
            }
        }
        return updated
    }
}
