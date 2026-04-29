package com.reservation.storage.rdb.order

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderStatus
import com.reservation.storage.rdb.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "idx_user_created", columnList = "user_id, created_at")],
    uniqueConstraints = [UniqueConstraint(name = "uk_order_key", columnNames = ["order_key"])],
)
class OrderEntity(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "order_key", nullable = false, length = 64)
    val orderKey: String,
) : BaseEntity() {

    fun toDomain(): Order = Order(
        id = id,
        productId = productId,
        userId = userId,
        totalAmount = totalAmount,
        status = status,
        orderKey = orderKey,
    )

    companion object {
        fun create(
            productId: Long,
            userId: Long,
            totalAmount: Long,
            orderKey: String,
        ): OrderEntity = OrderEntity(
            productId = productId,
            userId = userId,
            totalAmount = totalAmount,
            orderKey = orderKey,
        )
    }
}
