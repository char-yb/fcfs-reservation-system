package com.reservation.storage.rdb.order.product

import com.reservation.domain.order.OrderProduct
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "order_products",
    indexes = [
        Index(name = "idx_order_products_order_id", columnList = "order_id"),
        Index(name = "idx_order_products_product_id", columnList = "product_id"),
        Index(name = "idx_order_products_product_option_id", columnList = "product_option_id"),
    ],
)
class OrderProductEntity(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,
    @Column(name = "product_id", nullable = false)
    val productId: Long,
    @Column(name = "product_option_id", nullable = false)
    val productOptionId: Long,
    @Column(name = "ordered_at", nullable = false)
    val orderedAt: LocalDateTime,
    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = null,
    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    fun toDomain(): OrderProduct =
        OrderProduct(
            orderProductId = id,
            orderId = orderId,
            productId = productId,
            productOptionId = productOptionId,
            orderedAt = orderedAt,
            confirmedAt = confirmedAt,
            canceledAt = canceledAt,
        )

    companion object {
        fun from(orderProduct: OrderProduct): OrderProductEntity =
            OrderProductEntity(
                orderId = orderProduct.orderId,
                productId = orderProduct.productId,
                productOptionId = orderProduct.productOptionId,
                orderedAt = orderProduct.orderedAt,
                confirmedAt = orderProduct.confirmedAt,
                canceledAt = orderProduct.canceledAt,
            )
    }
}
