package com.reservation.domain.product

interface ProductRepository {
    fun findById(id: Long): Product?

    fun findAll(): List<Product>
}
