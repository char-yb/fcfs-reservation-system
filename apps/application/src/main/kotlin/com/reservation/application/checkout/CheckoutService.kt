package com.reservation.application.checkout

import com.reservation.application.checkout.result.CheckoutResult
import com.reservation.application.product.ProductService
import com.reservation.application.user.UserPointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CheckoutService(
    private val productService: ProductService,
    private val userPointService: UserPointService,
) {
    @Transactional(readOnly = true)
    fun checkout(
        productId: Long,
        userId: Long,
    ): CheckoutResult {
        val product = productService.getProduct(productId)
        product.validateSaleOpen()
        val stock = productService.getStock(productId)
        val userPoint = userPointService.getPoint(userId)
        return CheckoutResult(product = product, stock = stock, userPoint = userPoint)
    }
}
