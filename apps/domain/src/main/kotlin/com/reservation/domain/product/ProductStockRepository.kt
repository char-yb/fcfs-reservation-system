package com.reservation.domain.product

interface ProductStockRepository {
    fun findByProductOptionId(productOptionId: Long): ProductStock?

    fun findAll(): List<ProductStock>

    fun decrementStock(productOptionId: Long): Boolean

    fun incrementStock(productOptionId: Long)
}
