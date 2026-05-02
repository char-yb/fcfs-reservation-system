package com.reservation.domain.order

import java.time.LocalDateTime

interface OrderProductRepository {
    fun save(orderProduct: OrderProduct): OrderProduct

    fun findByOrderId(orderId: Long): List<OrderProduct>

    fun markConfirmedByOrderId(
        orderId: Long,
        confirmedAt: LocalDateTime,
    )

    fun markCanceledByOrderId(
        orderId: Long,
        canceledAt: LocalDateTime,
    )
}
