package com.reservation.storage.rdb.product

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductStockJpaRepository : JpaRepository<ProductStockEntity, Long> {
    @Modifying
    @Query(
        "UPDATE ProductStockEntity s SET s.remainingQuantity = s.remainingQuantity - 1, s.updatedAt = CURRENT_TIMESTAMP WHERE s.productId = :productId AND s.remainingQuantity > 0",
    )
    fun decrementStock(productId: Long): Int

    @Modifying
    @Query(
        "UPDATE ProductStockEntity s SET s.remainingQuantity = s.remainingQuantity + 1, s.updatedAt = CURRENT_TIMESTAMP WHERE s.productId = :productId",
    )
    fun incrementStock(productId: Long): Int
}
