package com.reservation.storage.rdb.product

import com.reservation.domain.product.BookingProductOption
import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductRepository
import com.reservation.domain.product.ProductType
import com.reservation.storage.rdb.product.booking.BookingScheduleJpaRepository
import com.reservation.storage.rdb.product.option.ProductOptionJpaRepository
import org.springframework.stereotype.Repository

@Repository
class ProductCoreRepository(
    private val productJpaRepository: ProductJpaRepository,
    private val productOptionJpaRepository: ProductOptionJpaRepository,
    private val bookingScheduleJpaRepository: BookingScheduleJpaRepository,
) : ProductRepository {
    override fun findById(id: Long): Product? = productJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findBookingOptionById(productOptionId: Long): BookingProductOption? {
        val option = productOptionJpaRepository.findById(productOptionId).orElse(null) ?: return null
        val product = productJpaRepository.findById(option.productId).orElse(null) ?: return null
        if (product.type != ProductType.BOOKING) return null
        val schedule = bookingScheduleJpaRepository.findByProductOptionId(option.id) ?: return null
        return BookingProductOption(
            product = product.toDomain(),
            option = option.toDomain(),
            schedule = schedule.toDomain(),
        )
    }
}
