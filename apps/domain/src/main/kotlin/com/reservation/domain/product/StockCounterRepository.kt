package com.reservation.domain.product

interface StockCounterRepository {
    fun decrement(productId: Long): Long

    fun increment(productId: Long)

    fun initialize(
        productId: Long,
        quantity: Int,
    )
}
