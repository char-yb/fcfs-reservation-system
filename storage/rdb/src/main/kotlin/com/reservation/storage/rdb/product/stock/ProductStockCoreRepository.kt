package com.reservation.storage.rdb.product.stock

import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductStockRepository
import org.springframework.stereotype.Repository

@Repository
class ProductStockCoreRepository(
    private val jpaRepository: ProductStockJpaRepository,
) : ProductStockRepository {
    override fun findByProductOptionId(productOptionId: Long): ProductStock? =
        jpaRepository.findById(productOptionId).orElse(null)?.toDomain()

    override fun findAll(): List<ProductStock> = jpaRepository.findAll().map { it.toDomain() }

    override fun decrementStock(productOptionId: Long): Boolean = jpaRepository.decrementStock(productOptionId) > 0

    override fun incrementStock(productOptionId: Long) {
        jpaRepository.incrementStock(productOptionId)
    }
}
