package com.reservation.storage.rdb.product

import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductRepository
import org.springframework.stereotype.Repository

@Repository
class ProductCoreRepository(
    private val jpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun findById(id: Long): Product? = jpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(): List<Product> = jpaRepository.findAll().map { it.toDomain() }
}
