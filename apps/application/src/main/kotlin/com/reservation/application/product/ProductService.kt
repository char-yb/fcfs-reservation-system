package com.reservation.application.product

import com.reservation.domain.product.BookingProductOption
import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository,
) {
    @Transactional(readOnly = true)
    fun getProduct(productId: Long): Product =
        productRepository.findById(productId)
            ?: throw ErrorException(ErrorType.PRODUCT_NOT_FOUND)

    @Transactional(readOnly = true)
    fun getBookingOption(productOptionId: Long): BookingProductOption =
        productRepository.findBookingOptionById(productOptionId)
            ?: throw ErrorException(ErrorType.PRODUCT_NOT_FOUND)
}
