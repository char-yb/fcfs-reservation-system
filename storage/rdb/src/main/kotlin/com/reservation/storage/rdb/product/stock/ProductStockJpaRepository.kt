package com.reservation.storage.rdb.product.stock

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductStockJpaRepository : JpaRepository<ProductStockEntity, Long> {
    @Modifying
    @Query(
        "UPDATE ProductStockEntity s SET s.remainingQuantity = s.remainingQuantity - 1, s.updatedAt = CURRENT_TIMESTAMP WHERE s.productOptionId = :productOptionId AND s.remainingQuantity > 0",
    )
    fun decrementStock(productOptionId: Long): Int

    @Modifying
    @Query(
        "UPDATE ProductStockEntity s SET s.remainingQuantity = s.remainingQuantity + 1, s.updatedAt = CURRENT_TIMESTAMP WHERE s.productOptionId = :productOptionId",
    )
    fun incrementStock(productOptionId: Long): Int
}
