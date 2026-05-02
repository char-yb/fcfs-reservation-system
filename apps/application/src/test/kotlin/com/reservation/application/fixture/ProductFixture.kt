package com.reservation.application.fixture

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductStock
import com.reservation.domain.user.UserPoint
import java.time.LocalDateTime

fun openProduct(
    id: Long = 1L,
    price: Long = 100_000L,
): Product {
    val now = LocalDateTime.of(2026, 4, 30, 10, 0)
    return Product(
        id = id,
        name = "room-$id",
        price = price,
        checkInAt = now.plusDays(1),
        checkOutAt = now.plusDays(2),
        saleOpenAt = now.minusMinutes(1),
    )
}

fun productStock(
    productId: Long = 1L,
    totalQuantity: Int = 10,
    remainingQuantity: Int = 1,
): ProductStock =
    ProductStock(
        productId = productId,
        totalQuantity = totalQuantity,
        remainingQuantity = remainingQuantity,
        version = 0L,
    )

fun pendingOrder(
    id: Long = 1L,
    productId: Long = 1L,
    userId: Long = 2L,
    totalAmount: Long = 100_000L,
    orderKey: String = "order-key",
): Order =
    Order(
        id = id,
        productId = productId,
        userId = userId,
        totalAmount = totalAmount,
        status = OrderStatus.PENDING,
        orderKey = orderKey,
    )

fun userPoint(
    userId: Long = 1L,
    pointBalance: Long = 10_000L,
): UserPoint = UserPoint(userId = userId, pointBalance = pointBalance)
