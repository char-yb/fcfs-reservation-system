package com.reservation.domain.order

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType

data class Order(
    val id: Long,
    val productId: Long,
    val userId: Long,
    val totalAmount: Long,
    val status: OrderStatus,
    val orderKey: String,
) {
    fun transitionTo(next: OrderStatus): Order {
        val allowed = ALLOWED_TRANSITIONS[status] ?: emptySet()
        if (next !in allowed) throw ErrorException(ErrorType.INVALID_ORDER_STATUS_TRANSITION)
        return copy(status = next)
    }

    companion object {
        private val ALLOWED_TRANSITIONS = mapOf(
            OrderStatus.PENDING to setOf(OrderStatus.PAID, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.PAID to setOf(OrderStatus.CONFIRMED, OrderStatus.FAILED, OrderStatus.CANCELLED),
        )
    }
}
