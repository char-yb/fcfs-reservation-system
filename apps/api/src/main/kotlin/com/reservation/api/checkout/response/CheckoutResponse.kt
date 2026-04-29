package com.reservation.api.checkout.response

import com.reservation.application.checkout.CheckoutResult
import java.time.LocalDateTime

data class CheckoutResponse(
    val product: ProductInfo,
    val user: UserInfo,
) {
    data class ProductInfo(
        val id: Long,
        val name: String,
        val price: Long,
        val checkInAt: LocalDateTime,
        val checkOutAt: LocalDateTime,
        val remainingQuantity: Int,
    )

    data class UserInfo(
        val id: Long,
        val availablePoint: Long,
    )

    companion object {
        fun from(
            result: CheckoutResult,
            userId: Long,
        ): CheckoutResponse =
            CheckoutResponse(
                product =
                    ProductInfo(
                        id = result.product.id,
                        name = result.product.name,
                        price = result.product.price,
                        checkInAt = result.product.checkInAt,
                        checkOutAt = result.product.checkOutAt,
                        remainingQuantity = result.stock.remainingQuantity,
                    ),
                user =
                    UserInfo(
                        id = userId,
                        availablePoint = result.userPoint.pointBalance,
                    ),
            )
    }
}
