package com.reservation.application.fixture

import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductStockRepository

class FakeProductStockRepository(
    initialStocks: List<ProductStock> = emptyList(),
    private val events: MutableList<String> = mutableListOf(),
) : ProductStockRepository {
    val stocks: MutableMap<Long, ProductStock> = initialStocks.associateBy { it.productOptionId }.toMutableMap()
    var incrementCalls = 0

    override fun findByProductOptionId(productOptionId: Long): ProductStock? = stocks[productOptionId]

    override fun findAll(): List<ProductStock> = stocks.values.toList()

    override fun decrementStock(productOptionId: Long): Boolean {
        val stock = stocks[productOptionId] ?: return false
        if (stock.remainingQuantity <= 0) return false
        stocks[productOptionId] = stock.copy(remainingQuantity = stock.remainingQuantity - 1)
        events.add("stock:decrement")
        return true
    }

    override fun incrementStock(productOptionId: Long) {
        incrementCalls += 1
        val stock = requireNotNull(stocks[productOptionId]) { "stock not found: $productOptionId" }
        stocks[productOptionId] = stock.copy(remainingQuantity = stock.remainingQuantity + 1)
        events.add("stock:increment")
    }
}
