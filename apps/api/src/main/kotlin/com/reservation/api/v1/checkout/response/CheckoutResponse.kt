package com.reservation.api.v1.checkout.response

import com.reservation.application.checkout.result.CheckoutResult
import com.reservation.domain.product.ProductInfo
import com.reservation.domain.user.UserInfo

data class CheckoutResponse(
    val product: ProductInfo,
    val user: UserInfo,
) {
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
