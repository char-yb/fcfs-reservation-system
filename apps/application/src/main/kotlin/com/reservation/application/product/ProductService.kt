package com.reservation.application.product

import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductRepository
import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductStockRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
) {
    @Transactional(readOnly = true)
    fun getProduct(productId: Long): Product =
        productRepository.findById(productId)
            ?: throw ErrorException(ErrorType.PRODUCT_NOT_FOUND)

    @Transactional(readOnly = true)
    fun getProductAll(): List<Product> = productRepository.findAll()

    @Transactional(readOnly = true)
    fun getStock(productId: Long): ProductStock =
        productStockRepository.findByProductId(productId)
            ?: throw ErrorException(ErrorType.PRODUCT_NOT_FOUND)

    @Transactional
    fun decrementStock(productId: Long): Boolean = productStockRepository.decrementStock(productId)

    @Transactional
    fun incrementStock(productId: Long) = productStockRepository.incrementStock(productId)
}
