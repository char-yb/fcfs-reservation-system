package com.reservation.application.fixture

import com.reservation.domain.order.OrderProduct
import com.reservation.domain.order.OrderProductRepository
import java.time.LocalDateTime

class FakeOrderProductRepository(
    initialOrderProducts: List<OrderProduct> = emptyList(),
) : OrderProductRepository {
    val orderProducts: MutableMap<Long, OrderProduct> = initialOrderProducts.associateBy { it.orderProductId }.toMutableMap()
    private var nextId = (orderProducts.keys.maxOrNull() ?: 0L) + 1L

    override fun save(orderProduct: OrderProduct): OrderProduct {
        val saved =
            if (orderProduct.orderProductId == 0L) {
                orderProduct.copy(orderProductId = nextId++)
            } else {
                orderProduct
            }
        orderProducts[saved.orderProductId] = saved
        return saved
    }

    override fun findByOrderId(orderId: Long): List<OrderProduct> = orderProducts.values.filter { it.orderId == orderId }

    override fun markConfirmedByOrderId(
        orderId: Long,
        confirmedAt: LocalDateTime,
    ) {
        orderProducts
            .filterValues { it.orderId == orderId }
            .forEach { (id, orderProduct) ->
                orderProducts[id] = orderProduct.copy(confirmedAt = confirmedAt)
            }
    }

    override fun markCanceledByOrderId(
        orderId: Long,
        canceledAt: LocalDateTime,
    ) {
        orderProducts
            .filterValues { it.orderId == orderId }
            .forEach { (id, orderProduct) ->
                orderProducts[id] = orderProduct.copy(canceledAt = canceledAt)
            }
    }
}
