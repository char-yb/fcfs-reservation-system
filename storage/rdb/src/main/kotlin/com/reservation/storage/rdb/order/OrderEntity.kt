package com.reservation.storage.rdb.order

import com.reservation.domain.order.Order
import com.reservation.domain.order.OrderStatus
import com.reservation.storage.rdb.common.BaseEntity
import com.reservation.support.money.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal

@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "idx_user_created", columnList = "user_id, created_at")],
    uniqueConstraints = [UniqueConstraint(name = "uk_order_key", columnNames = ["order_key"])],
)
class OrderEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    val totalAmount: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,
    @Column(name = "order_key", nullable = false, length = 64)
    val orderKey: String,
) : BaseEntity() {
    fun toDomain(): Order =
        Order(
            id = id,
            userId = userId,
            totalAmount = Money(totalAmount),
            status = status,
            orderKey = orderKey,
        )

    companion object {
        fun create(
            userId: Long,
            totalAmount: BigDecimal,
            orderKey: String,
        ): OrderEntity =
            OrderEntity(
                userId = userId,
                totalAmount = totalAmount,
                orderKey = orderKey,
            )
    }
}
