package com.reservation.api.v1.checkout.response

import com.reservation.domain.product.ProductType
import java.math.BigDecimal
import java.time.LocalDateTime

data class CheckoutProductResponse(
    val productId: Long,
    val productOptionId: Long,
    val productName: String,
    val productType: ProductType,
    val optionName: String,
    val price: BigDecimal,
    val checkInAt: LocalDateTime,
    val checkOutAt: LocalDateTime,
    val remainingQuantity: Int,
)
