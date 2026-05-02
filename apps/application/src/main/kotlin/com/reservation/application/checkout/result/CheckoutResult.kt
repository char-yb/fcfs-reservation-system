package com.reservation.application.checkout.result

import com.reservation.domain.product.BookingProductOption
import com.reservation.domain.product.ProductStock
import com.reservation.domain.user.UserPoint

data class CheckoutResult(
    val bookingOption: BookingProductOption,
    val stock: ProductStock,
    val userPoint: UserPoint,
)
