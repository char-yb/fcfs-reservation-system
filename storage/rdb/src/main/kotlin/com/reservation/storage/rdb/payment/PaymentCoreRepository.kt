package com.reservation.storage.rdb.payment

import com.reservation.domain.payment.Payment
import com.reservation.domain.payment.PaymentRepository
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
                        amount = payment.amount,
                        externalRequestId = payment.externalRequestId,
                    ).also { it.pgTransactionId = payment.pgTransactionId }
            }
        return jpaRepository.saveAll(entities).map { it.toDomain() }
    }

    override fun findByOrderId(orderId: Long): List<Payment> = jpaRepository.findAllByOrderId(orderId).map { it.toDomain() }
}
