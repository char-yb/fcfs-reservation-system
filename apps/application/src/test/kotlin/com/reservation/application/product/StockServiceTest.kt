package com.reservation.application.product

import com.reservation.application.fixture.FakeProductStockRepository
import com.reservation.application.fixture.FakeStockCounterRepository
import com.reservation.application.fixture.RecordingDistributedLock
import com.reservation.application.fixture.productStock
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.redis.RedisUnavailableException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class StockServiceTest :
    StringSpec({
        "재고를 조회한다" {
            val stock = productStock(remainingQuantity = 7)
            val service =
                StockService(
                    productStockRepository = FakeProductStockRepository(listOf(stock)),
                    stockCounterRepository = FakeStockCounterRepository(),
                    distributedLock = RecordingDistributedLock(),
                )

            service.getStock(1L) shouldBe stock
        }

        "재고가 없으면 조회를 거부한다" {
            val service =
                StockService(
                    productStockRepository = FakeProductStockRepository(),
                    stockCounterRepository = FakeStockCounterRepository(),
                    distributedLock = RecordingDistributedLock(),
                )

            val exception =
                shouldThrow<ErrorException> {
                    service.getStock(1L)
                }

            exception.errorType shouldBe ErrorType.PRODUCT_NOT_FOUND
        }

        "DB 재고 기준으로 Redis 카운터를 초기화한다" {
            val stock = productStock(remainingQuantity = 7)
            val counterRepository = FakeStockCounterRepository()
            val service =
                StockService(
                    productStockRepository = FakeProductStockRepository(listOf(stock)),
                    stockCounterRepository = counterRepository,
                    distributedLock = RecordingDistributedLock(),
                )

            service.initializeStockCounter(1L)

            counterRepository.initialized[1L] shouldBe 7
            counterRepository.remaining shouldBe 7L
        }

        "재고 카운터 차감 후 분산락 안에서 작업을 실행한다" {
            val stockRepository = FakeProductStockRepository()
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val distributedLock = RecordingDistributedLock()
            val service = StockService(stockRepository, counterRepository, distributedLock)

            val result = service.executeWithStockGuard(productId = 1L) { "reserved" }

            result shouldBe "reserved"
            counterRepository.remaining shouldBe 1L
            distributedLock.calls.single().key shouldBe "lock:booking:1"
            distributedLock.calls.single().waitTime shouldBe Duration.ofMillis(100)
            distributedLock.calls.single().leaseTime shouldBe Duration.ofMillis(5_000)
        }

        "카운터 차감 결과가 음수이면 매진으로 거부하고 카운터를 복구한다" {
            val counterRepository = FakeStockCounterRepository(initialRemaining = 0L)
            val service = StockService(FakeProductStockRepository(), counterRepository, RecordingDistributedLock())
            var fallbackExecuted = false

            val exception =
                shouldThrow<ErrorException> {
                    service.executeWithStockGuard(
                        productId = 1L,
                        action = { "reserved" },
                        fallbackAction = {
                            fallbackExecuted = true
                            "fallback"
                        },
                    )
                }

            exception.errorType shouldBe ErrorType.STOCK_SOLD_OUT
            counterRepository.remaining shouldBe 0L
            counterRepository.incrementCalls shouldBe 1
            fallbackExecuted shouldBe false
        }

        "락 내부 작업이 실패하면 카운터를 복구한다" {
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val service = StockService(FakeProductStockRepository(), counterRepository, RecordingDistributedLock())

            shouldThrow<IllegalStateException> {
                service.executeWithStockGuard(productId = 1L) {
                    throw IllegalStateException("failed")
                }
            }

            counterRepository.remaining shouldBe 2L
            counterRepository.incrementCalls shouldBe 1
        }

        "Redis 카운터 장애 시 DB-only fallback 작업을 실행한다" {
            val counterRepository =
                FakeStockCounterRepository(
                    initialRemaining = 2L,
                    decrementFailure = RedisUnavailableException("redis down"),
                )
            val distributedLock = RecordingDistributedLock()
            val service = StockService(FakeProductStockRepository(), counterRepository, distributedLock)
            var primaryExecuted = false

            val result =
                service.executeWithStockGuard(
                    productId = 1L,
                    action = {
                        primaryExecuted = true
                        "primary"
                    },
                    fallbackAction = { "fallback" },
                )

            result shouldBe "fallback"
            primaryExecuted shouldBe false
            distributedLock.calls shouldBe emptyList()
            counterRepository.remaining shouldBe 2L
        }

        "Redis 락 장애 시 카운터를 복구하고 DB-only fallback 작업을 실행한다" {
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val distributedLock = RecordingDistributedLock(RedisUnavailableException("lock down"))
            val service = StockService(FakeProductStockRepository(), counterRepository, distributedLock)

            val result =
                service.executeWithStockGuard(
                    productId = 1L,
                    action = { "primary" },
                    fallbackAction = { "fallback" },
                )

            result shouldBe "fallback"
            distributedLock.calls.single().key shouldBe "lock:booking:1"
            counterRepository.remaining shouldBe 2L
            counterRepository.incrementCalls shouldBe 1
        }

        "락 획득 실패는 Redis 장애 fallback을 실행하지 않는다" {
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val service =
                StockService(
                    FakeProductStockRepository(),
                    counterRepository,
                    RecordingDistributedLock(ErrorException(ErrorType.LOCK_ACQUISITION_FAILED)),
                )
            var fallbackExecuted = false

            val exception =
                shouldThrow<ErrorException> {
                    service.executeWithStockGuard(
                        productId = 1L,
                        action = { "primary" },
                        fallbackAction = {
                            fallbackExecuted = true
                            "fallback"
                        },
                    )
                }

            exception.errorType shouldBe ErrorType.LOCK_ACQUISITION_FAILED
            fallbackExecuted shouldBe false
            counterRepository.remaining shouldBe 2L
            counterRepository.incrementCalls shouldBe 1
        }
    })
