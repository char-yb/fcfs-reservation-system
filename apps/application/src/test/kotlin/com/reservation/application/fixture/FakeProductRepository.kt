package com.reservation.application.fixture

import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductRepository

class FakeProductRepository(
    initialProducts: List<Product> = emptyList(),
) : ProductRepository {
    val products: MutableMap<Long, Product> = initialProducts.associateBy { it.id }.toMutableMap()

    override fun findById(id: Long): Product? = products[id]

    override fun findAll(): List<Product> = products.values.toList()
}
