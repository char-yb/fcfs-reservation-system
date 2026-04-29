package com.reservation.storage.rdb.product

import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductStockRepository
import org.springframework.stereotype.Repository

@Repository
class ProductStockCoreRepository(
    private val jpaRepository: ProductStockJpaRepository,
) : ProductStockRepository {
    override fun findByProductId(productId: Long): ProductStock? = jpaRepository.findById(productId).orElse(null)?.toDomain()

    override fun decrementStock(productId: Long): Boolean = jpaRepository.decrementStock(productId) > 0

    override fun incrementStock(productId: Long) {
        jpaRepository.incrementStock(productId)
    }
}
