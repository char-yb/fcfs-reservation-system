package com.reservation.storage.rdb.payment

import com.reservation.domain.payment.Payment
import com.reservation.domain.payment.PaymentRepository
import com.reservation.domain.payment.PaymentStatus
import org.springframework.stereotype.Repository

@Repository
class PaymentCoreRepository(
    private val jpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun saveAll(payments: List<Payment>): List<Payment> {
        val entities =
            payments.map { payment ->
                PaymentEntity
                    .create(
                        orderId = payment.orderId,
                        method = payment.method,
                        amount = payment.amount.amount,
                        externalRequestId = payment.externalRequestId,
                    ).also {
                        it.status = payment.status
                        it.pgTransactionId = payment.pgTransactionId
                    }
            }
        return jpaRepository.saveAll(entities).map { it.toDomain() }
    }

    override fun findByOrderId(orderId: Long): List<Payment> = jpaRepository.findAllByOrderId(orderId).map { it.toDomain() }

    override fun updateStatusByOrderIdAndTransactionIds(
        orderId: Long,
        transactionIds: List<String>,
        status: PaymentStatus,
    ): Int {
        if (transactionIds.isEmpty()) return 0
        return jpaRepository.updateStatusByOrderIdAndTransactionIds(orderId, transactionIds, status)
    }
}
