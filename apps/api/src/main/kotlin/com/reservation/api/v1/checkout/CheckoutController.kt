package com.reservation.api.v1.checkout

import com.reservation.api.response.ApiResponse
import com.reservation.api.v1.checkout.response.CheckoutResponse
import com.reservation.application.checkout.CheckoutService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/checkout")
class CheckoutController(
    private val checkoutService: CheckoutService,
) {
    @GetMapping("/{userId}")
    fun checkout(
        @PathVariable userId: Long,
        @RequestParam productOptionId: Long,
    ): ApiResponse<CheckoutResponse> {
        val result = checkoutService.checkout(productOptionId = productOptionId, userId = userId)
        return ApiResponse.success(CheckoutResponse.from(result, userId))
    }
}
