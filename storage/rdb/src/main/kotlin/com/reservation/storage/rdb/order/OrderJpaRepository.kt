package com.reservation.storage.rdb.order

import com.reservation.domain.order.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findByOrderKey(orderKey: String): Optional<OrderEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE OrderEntity o
        SET o.status = :nextStatus, o.updatedAt = CURRENT_TIMESTAMP
        WHERE o.id = :id AND o.status = :currentStatus
        """,
    )
    fun updateStatusIfCurrent(
        id: Long,
        currentStatus: OrderStatus,
        nextStatus: OrderStatus,
    ): Int
}
