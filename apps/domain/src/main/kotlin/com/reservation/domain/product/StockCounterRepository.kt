package com.reservation.domain.product

interface StockCounterRepository {
    fun decrement(productOptionId: Long): Long

    fun increment(productOptionId: Long)

    fun initialize(
        productOptionId: Long,
        quantity: Int,
    )
}
