package com.reservation.application.checkout

import com.reservation.application.checkout.result.CheckoutResult
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockService
import com.reservation.application.user.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CheckoutService(
    private val productService: ProductService,
    private val stockService: StockService,
    private val userPointService: UserPointService,
) {
    @Transactional(readOnly = true)
    fun checkout(
        productOptionId: Long,
        userId: Long,
    ): CheckoutResult {
        val bookingOption = productService.getBookingOption(productOptionId)
        bookingOption.validateSaleOpen()
        val stock = stockService.getStock(productOptionId)
        val userPoint = userPointService.getPoint(userId)
        return CheckoutResult(bookingOption = bookingOption, stock = stock, userPoint = userPoint)
    }
}
