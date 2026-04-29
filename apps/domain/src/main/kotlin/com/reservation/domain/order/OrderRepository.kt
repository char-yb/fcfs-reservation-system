package com.reservation.domain.order

interface OrderRepository {
    fun findById(id: Long): Order?

    fun findByOrderKey(orderKey: String): Order?

    fun save(order: Order): Order

    fun updateStatus(
        id: Long,
        status: OrderStatus,
    ): Order
}
