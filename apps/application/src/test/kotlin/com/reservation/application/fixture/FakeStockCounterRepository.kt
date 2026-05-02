package com.reservation.application.fixture

import com.reservation.domain.product.StockCounterRepository

class FakeStockCounterRepository(
    initialRemaining: Long = 1L,
    private val decrementFailure: RuntimeException? = null,
    private val incrementFailure: RuntimeException? = null,
) : StockCounterRepository {
    var remaining = initialRemaining
    var incrementCalls = 0
    val initialized = mutableMapOf<Long, Int>()

    override fun decrement(productId: Long): Long {
        decrementFailure?.let { throw it }
        remaining -= 1
        return remaining
    }

    override fun increment(productId: Long) {
        incrementFailure?.let { throw it }
        incrementCalls += 1
        remaining += 1
    }

    override fun initialize(
        productId: Long,
        quantity: Int,
    ) {
        initialized[productId] = quantity
        remaining = quantity.toLong()
    }
}
