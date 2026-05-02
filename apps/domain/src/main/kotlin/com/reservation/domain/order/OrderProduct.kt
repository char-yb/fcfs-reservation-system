package com.reservation.domain.order

import java.time.LocalDateTime

data class OrderProduct(
    val orderProductId: Long,
    val orderId: Long,
    val productId: Long,
    val productOptionId: Long,
    val orderedAt: LocalDateTime,
    val confirmedAt: LocalDateTime? = null,
    val canceledAt: LocalDateTime? = null,
) {
    companion object {
        fun ordered(
            orderId: Long,
            productId: Long,
            productOptionId: Long,
            orderedAt: LocalDateTime = LocalDateTime.now(),
        ): OrderProduct =
            OrderProduct(
                orderProductId = 0L,
                orderId = orderId,
                productId = productId,
                productOptionId = productOptionId,
                orderedAt = orderedAt,
            )
    }
}
