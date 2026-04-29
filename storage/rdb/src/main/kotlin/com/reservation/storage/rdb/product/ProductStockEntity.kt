package com.reservation.storage.rdb.product

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
    @Column(name = "product_id")
    val productId: Long,

    @Column(name = "total_quantity", nullable = false)
    val totalQuantity: Int,

    @Column(name = "remaining_quantity", nullable = false)
    var remainingQuantity: Int,

    // conditional UPDATE(WHERE remaining > 0)로 정합성 보장하므로 @Version 미사용.
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toDomain(): ProductStock = ProductStock(
        productId = productId,
        totalQuantity = totalQuantity,
        remainingQuantity = remainingQuantity,
        version = version,
    )
}
