package com.reservation.api.v1.checkout.response

import com.reservation.application.checkout.result.CheckoutResult

data class CheckoutResponse(
    val product: CheckoutProductResponse,
    val user: CheckoutUserResponse,
) {
    companion object {
        fun from(
            result: CheckoutResult,
            userId: Long,
        ): CheckoutResponse =
            CheckoutResponse(
                product =
                    CheckoutProductResponse(
                        productId = result.bookingOption.productId,
                        productOptionId = result.bookingOption.id,
                        productName = result.bookingOption.productName,
                        productType = result.bookingOption.productType,
                        optionName = result.bookingOption.optionName,
                        price = result.bookingOption.price.amount,
                        checkInAt = result.bookingOption.schedule.checkInAt,
                        checkOutAt = result.bookingOption.schedule.checkOutAt,
                        remainingQuantity = result.stock.remainingQuantity,
                    ),
                user =
                    CheckoutUserResponse(
                        id = userId,
                        availablePoint = result.userPoint.pointBalance,
                    ),
            )
    }
}
