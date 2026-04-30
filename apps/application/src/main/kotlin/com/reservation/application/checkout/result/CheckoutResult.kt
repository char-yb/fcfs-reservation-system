package com.reservation.application.checkout.result

import com.reservation.domain.product.Product
import com.reservation.domain.product.ProductStock
import com.reservation.domain.user.UserPoint

data class CheckoutResult(
    val product: Product,
    val stock: ProductStock,
    val userPoint: UserPoint,
)
