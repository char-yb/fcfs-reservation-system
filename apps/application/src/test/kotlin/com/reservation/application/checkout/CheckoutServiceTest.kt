package com.reservation.application.checkout

import com.reservation.application.fixture.FakeProductRepository
import com.reservation.application.fixture.FakeProductStockRepository
import com.reservation.application.fixture.FakeStockCounterRepository
import com.reservation.application.fixture.FakeUserPointRepository
import com.reservation.application.fixture.RecordingDistributedLock
import com.reservation.application.fixture.openProduct
import com.reservation.application.fixture.productStock
import com.reservation.application.fixture.userPoint
import com.reservation.application.product.ProductService
import com.reservation.application.product.StockService
import com.reservation.application.user.UserPointService
import com.reservation.domain.product.Product
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class CheckoutServiceTest :
    StringSpec({
        "체크아웃에 상품과 재고와 사용자 포인트를 반환한다" {
            val product = openProduct(id = 1L)
            val stock = productStock(remainingQuantity = 7)
            val point = userPoint(userId = 2L, pointBalance = 30_000L)
            val service =
                CheckoutService(
                    productService =
                        ProductService(
                            productRepository = FakeProductRepository(listOf(product)),
                        ),
                    stockService =
                        StockService(
                            productStockRepository = FakeProductStockRepository(listOf(stock)),
                            stockCounterRepository = FakeStockCounterRepository(),
                            distributedLock = RecordingDistributedLock(),
                        ),
                    userPointService = UserPointService(FakeUserPointRepository(listOf(point))),
                )

            val result = service.checkout(productId = 1L, userId = 2L)

            result.product shouldBe product
            result.stock shouldBe stock
            result.userPoint shouldBe point
        }

        "판매 시작 전 상품은 체크아웃을 거부한다" {
            val now = LocalDateTime.now()
            val product =
                Product(
                    id = 1L,
                    name = "room",
                    price = 100_000L,
                    checkInAt = now.plusDays(1),
                    checkOutAt = now.plusDays(2),
                    saleOpenAt = now.plusDays(1),
                )
            val service =
                CheckoutService(
                    productService =
                        ProductService(
                            productRepository = FakeProductRepository(listOf(product)),
                        ),
                    stockService =
                        StockService(
                            productStockRepository = FakeProductStockRepository(),
                            stockCounterRepository = FakeStockCounterRepository(),
                            distributedLock = RecordingDistributedLock(),
                        ),
                    userPointService = UserPointService(FakeUserPointRepository()),
                )

            val exception =
                shouldThrow<ErrorException> {
                    service.checkout(productId = 1L, userId = 2L)
                }

            exception.errorType shouldBe ErrorType.SALE_NOT_OPEN
        }
    })
