package com.reservation.application.product

import com.reservation.application.fixture.FakeProductRepository
import com.reservation.application.fixture.FakeProductStockRepository
import com.reservation.application.fixture.FakeStockCounterRepository
import com.reservation.application.fixture.openProduct
import com.reservation.domain.product.ProductStock
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
                    productStockRepository = FakeProductStockRepository(),
                    stockCounterRepository = FakeStockCounterRepository(),
                )

            service.getProduct(1L) shouldBe product
        }

        "상품이 없으면 거부한다" {
            val service =
                ProductService(
                    productRepository = FakeProductRepository(),
                    productStockRepository = FakeProductStockRepository(),
                    stockCounterRepository = FakeStockCounterRepository(),
                )

            val exception =
                shouldThrow<ErrorException> {
                    service.getProduct(1L)
                }

            exception.errorType shouldBe ErrorType.PRODUCT_NOT_FOUND
        }

        "재고를 차감하고 복구한다" {
            val stock = ProductStock(productId = 1L, totalQuantity = 10, remainingQuantity = 1, version = 0L)
            val stockRepository = FakeProductStockRepository(listOf(stock))
            val service =
                ProductService(
                    productRepository = FakeProductRepository(),
                    productStockRepository = stockRepository,
                    stockCounterRepository = FakeStockCounterRepository(),
                )

            service.decrementStock(1L) shouldBe true
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 0

            service.incrementStock(1L)
            stockRepository.stocks[1L]?.remainingQuantity shouldBe 1
        }

        "DB 재고 기준으로 Redis 카운터를 초기화한다" {
            val stock = ProductStock(productId = 1L, totalQuantity = 10, remainingQuantity = 7, version = 0L)
            val counterRepository = FakeStockCounterRepository()
            val service =
                ProductService(
                    productRepository = FakeProductRepository(),
                    productStockRepository = FakeProductStockRepository(listOf(stock)),
                    stockCounterRepository = counterRepository,
                )

            service.initializeStockCounter(1L)

            counterRepository.initialized[1L] shouldBe 7
            counterRepository.remaining shouldBe 7L
        }
    })
