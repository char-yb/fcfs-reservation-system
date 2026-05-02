package com.reservation.application.fixture

import com.reservation.domain.product.BookingProductOption
import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductRepository

class FakeProductRepository(
    initialProducts: List<Product> = emptyList(),
    initialBookingOptions: List<BookingProductOption> = emptyList(),
) : ProductRepository {
    val products: MutableMap<Long, Product> = initialProducts.associateBy { it.id }.toMutableMap()
    val bookingOptions: MutableMap<Long, BookingProductOption> = initialBookingOptions.associateBy { it.id }.toMutableMap()

    override fun findById(id: Long): Product? = products[id]

    override fun findBookingOptionById(productOptionId: Long): BookingProductOption? = bookingOptions[productOptionId]
}
