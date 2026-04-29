package com.reservation.domain.product

interface ProductStockRepository {
    fun findByProductId(productId: Long): ProductStock?

    fun decrementStock(productId: Long): Boolean

    fun incrementStock(productId: Long)
}
