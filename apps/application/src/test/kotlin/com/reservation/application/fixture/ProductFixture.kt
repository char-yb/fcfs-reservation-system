package com.reservation.application.fixture

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderProduct
import com.reservation.domain.order.OrderStatus
import com.reservation.domain.product.BookingProductOption
import com.reservation.domain.product.BookingSchedule
import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductOption
import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductType
import com.reservation.domain.user.UserPoint
import java.time.LocalDateTime

fun openProduct(id: Long = 1L): Product =
    Product(
        id = id,
        name = "room-$id",
        type = ProductType.BOOKING,
    )

fun openBookingOption(
    id: Long = 1L,
    productId: Long = 1L,
    price: Long = 100_000L,
): BookingProductOption {
    val now = LocalDateTime.of(2026, 4, 30, 10, 0)
    return BookingProductOption(
        product = openProduct(id = productId),
        option =
            ProductOption(
                id = id,
                productId = productId,
                name = "standard-$id",
                price = price,
                saleOpenAt = now.minusMinutes(1),
            ),
        schedule =
            BookingSchedule(
                checkInAt = now.plusDays(1),
                checkOutAt = now.plusDays(2),
            ),
    )
}

fun productStock(
    productOptionId: Long = 1L,
    totalQuantity: Int = 10,
    remainingQuantity: Int = 1,
): ProductStock =
    ProductStock(
        productOptionId = productOptionId,
        totalQuantity = totalQuantity,
        remainingQuantity = remainingQuantity,
    )

fun pendingOrder(
    id: Long = 1L,
    userId: Long = 2L,
    totalAmount: Long = 100_000L,
    orderKey: String = "order-key",
): Order =
    Order(
        id = id,
        userId = userId,
        totalAmount = totalAmount,
        status = OrderStatus.PENDING,
        orderKey = orderKey,
    )

fun orderProduct(
    orderProductId: Long = 1L,
    orderId: Long = 1L,
    productId: Long = 1L,
    productOptionId: Long = 1L,
    orderedAt: LocalDateTime = LocalDateTime.of(2026, 4, 30, 10, 0),
): OrderProduct =
    OrderProduct(
        orderProductId = orderProductId,
        orderId = orderId,
        productId = productId,
        productOptionId = productOptionId,
        orderedAt = orderedAt,
    )

fun userPoint(
    userId: Long = 1L,
    pointBalance: Long = 10_000L,
): UserPoint = UserPoint(userId = userId, pointBalance = pointBalance)
