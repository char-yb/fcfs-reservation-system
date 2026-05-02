package com.reservation.application.fixture

import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductStockRepository

class FakeProductStockRepository(
    initialStocks: List<ProductStock> = emptyList(),
    private val events: MutableList<String> = mutableListOf(),
) : ProductStockRepository {
    val stocks: MutableMap<Long, ProductStock> = initialStocks.associateBy { it.productId }.toMutableMap()
    var incrementCalls = 0

    override fun findByProductId(productId: Long): ProductStock? = stocks[productId]

    override fun decrementStock(productId: Long): Boolean {
        val stock = stocks[productId] ?: return false
        if (stock.remainingQuantity <= 0) return false
        stocks[productId] = stock.copy(remainingQuantity = stock.remainingQuantity - 1)
        events.add("stock:decrement")
        return true
    }

    override fun incrementStock(productId: Long) {
        incrementCalls += 1
        val stock = requireNotNull(stocks[productId]) { "stock not found: $productId" }
        stocks[productId] = stock.copy(remainingQuantity = stock.remainingQuantity + 1)
        events.add("stock:increment")
    }
}
