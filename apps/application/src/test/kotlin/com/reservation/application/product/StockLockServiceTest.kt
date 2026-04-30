package com.reservation.application.product

import com.reservation.application.fixture.FakeStockCounterRepository
import com.reservation.application.fixture.RecordingDistributedLock
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class StockLockServiceTest :
    StringSpec({
        "재고 카운터 차감 후 분산락 안에서 작업을 실행한다" {
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val distributedLock = RecordingDistributedLock()
            val service = StockLockService(counterRepository, distributedLock)

            val result = service.executeWithStockLock(productId = 1L) { "reserved" }

            result shouldBe "reserved"
            counterRepository.remaining shouldBe 1L
            distributedLock.calls.single().key shouldBe "lock:booking:1"
            distributedLock.calls.single().waitTime shouldBe Duration.ofMillis(100)
            distributedLock.calls.single().leaseTime shouldBe Duration.ofMillis(5_000)
        }

        "카운터 차감 결과가 음수이면 매진으로 거부하고 카운터를 복구한다" {
            val counterRepository = FakeStockCounterRepository(initialRemaining = 0L)
            val service = StockLockService(counterRepository, RecordingDistributedLock())

            val exception =
                shouldThrow<ErrorException> {
                    service.executeWithStockLock(productId = 1L) { "reserved" }
                }

            exception.errorType shouldBe ErrorType.STOCK_SOLD_OUT
            counterRepository.remaining shouldBe 0L
            counterRepository.incrementCalls shouldBe 1
        }

        "락 내부 작업이 실패하면 카운터를 복구한다" {
            val counterRepository = FakeStockCounterRepository(initialRemaining = 2L)
            val service = StockLockService(counterRepository, RecordingDistributedLock())

            shouldThrow<IllegalStateException> {
                service.executeWithStockLock(productId = 1L) {
                    throw IllegalStateException("failed")
                }
            }

            counterRepository.remaining shouldBe 2L
            counterRepository.incrementCalls shouldBe 1
        }
    })
