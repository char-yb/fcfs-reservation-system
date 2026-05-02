package com.reservation.storage.rdb.product.stock

import com.reservation.domain.product.ProductStock
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "product_stock")
class ProductStockEntity(
    @Id
    @Column(name = "product_option_id")
    val productOptionId: Long,
    @Column(name = "total_quantity", nullable = false)
    val totalQuantity: Int,
    @Column(name = "remaining_quantity", nullable = false)
    var remainingQuantity: Int,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toDomain(): ProductStock =
        ProductStock(
            productOptionId = productOptionId,
            totalQuantity = totalQuantity,
            remainingQuantity = remainingQuantity,
        )
}
