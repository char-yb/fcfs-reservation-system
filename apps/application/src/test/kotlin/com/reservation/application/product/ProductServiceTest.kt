package com.reservation.application.product

import com.reservation.application.fixture.FakeProductRepository
import com.reservation.application.fixture.openProduct
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProductServiceTest :
    StringSpec({
        "상품을 조회한다" {
            val product = openProduct(id = 1L)
            val service =
                ProductService(
                    productRepository = FakeProductRepository(listOf(product)),
                )

            service.getProduct(1L) shouldBe product
        }

        "상품이 없으면 거부한다" {
            val service =
                ProductService(
                    productRepository = FakeProductRepository(),
                )

            val exception = shouldThrow<ErrorException> { service.getProduct(1L) }
            exception.errorType shouldBe ErrorType.PRODUCT_NOT_FOUND
        }
    })
