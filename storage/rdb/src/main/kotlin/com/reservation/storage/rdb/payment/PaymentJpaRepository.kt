package com.reservation.storage.rdb.payment

import com.reservation.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findAllByOrderId(orderId: Long): List<PaymentEntity>

    @Modifying
    @Query(
        """
        UPDATE PaymentEntity p
        SET p.status = :status
        WHERE p.orderId = :orderId
          AND p.pgTransactionId IN :transactionIds
        """,
    )
    fun updateStatusByOrderIdAndTransactionIds(
        orderId: Long,
        transactionIds: List<String>,
        status: PaymentStatus,
    ): Int
}
