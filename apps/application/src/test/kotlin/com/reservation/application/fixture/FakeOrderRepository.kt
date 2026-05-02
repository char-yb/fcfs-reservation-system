package com.reservation.application.fixture

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderRepository
import com.reservation.domain.order.OrderStatus

class FakeOrderRepository(
    initialOrders: List<Order> = emptyList(),
    private val events: MutableList<String> = mutableListOf(),
    private val statusUpdateFailures: Map<OrderStatus, RuntimeException> = emptyMap(),
) : OrderRepository {
    val orders: MutableMap<Long, Order> = initialOrders.associateBy { it.id }.toMutableMap()
    private var nextId = (orders.keys.maxOrNull() ?: 0L) + 1L

    override fun findById(id: Long): Order? = orders[id]

    override fun findByOrderKey(orderKey: String): Order? = orders.values.firstOrNull { it.orderKey == orderKey }

    override fun save(order: Order): Order {
        val saved =
            if (order.id == 0L) {
                order.copy(id = nextId++)
            } else {
                order
            }
        orders[saved.id] = saved
        events.add("order:${saved.status.name}")
        return saved
    }

    override fun updateStatus(
        id: Long,
        status: OrderStatus,
    ): Order {
        statusUpdateFailures[status]?.let { throw it }
        val updated = requireNotNull(orders[id]) { "order not found: $id" }.copy(status = status)
        orders[id] = updated
        events.add("order:${status.name}")
        return updated
    }

    override fun updateStatusIfCurrent(
        id: Long,
        currentStatus: OrderStatus,
        nextStatus: OrderStatus,
    ): Order? {
        statusUpdateFailures[nextStatus]?.let { throw it }
        val order = orders[id] ?: return null
        if (order.status != currentStatus) return null
        val updated = order.copy(status = nextStatus)
        orders[id] = updated
        events.add("order:${nextStatus.name}")
        return updated
    }
}
