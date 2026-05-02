package com.reservation.storage.rdb.order.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface OrderProductJpaRepository : JpaRepository<OrderProductEntity, Long> {
    fun findByOrderId(orderId: Long): List<OrderProductEntity>

    @Modifying
    @Query(
        "UPDATE OrderProductEntity op SET op.confirmedAt = :confirmedAt WHERE op.orderId = :orderId AND op.confirmedAt IS NULL",
    )
    fun markConfirmedByOrderId(
        orderId: Long,
        confirmedAt: LocalDateTime,
    ): Int

    @Modifying
    @Query(
        "UPDATE OrderProductEntity op SET op.canceledAt = :canceledAt WHERE op.orderId = :orderId AND op.canceledAt IS NULL",
    )
    fun markCanceledByOrderId(
        orderId: Long,
        canceledAt: LocalDateTime,
    ): Int
}
