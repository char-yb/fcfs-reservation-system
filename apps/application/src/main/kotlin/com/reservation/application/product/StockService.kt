package com.reservation.application.product

import com.reservation.domain.product.ProductStock
import com.reservation.domain.product.ProductStockRepository
import com.reservation.domain.product.StockCounterRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import com.reservation.support.lock.DistributedLock
import com.reservation.support.redis.RedisUnavailableException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class StockService(
    private val productStockRepository: ProductStockRepository,
    private val stockCounterRepository: StockCounterRepository,
    private val distributedLock: DistributedLock,
) {
    private val log by logger()

    @Transactional(readOnly = true)
    fun getStock(productOptionId: Long): ProductStock = findStockOrThrow(productOptionId)

    @Transactional(readOnly = true)
    fun getStocks(): List<ProductStock> = productStockRepository.findAll()

    @Transactional(readOnly = true)
    fun initializeStockCounter(productOptionId: Long) {
        stockCounterRepository.initialize(productOptionId, findStockOrThrow(productOptionId).remainingQuantity)
    }

    /**
     * L1(Redis counter) + L2(fair lock) 재고 예약 흐름 실행.
     * Redis adapter가 장애 또는 circuit open 상태를 RedisUnavailableException으로 변환하면
     * L1/L2를 건너뛰고 fallbackAction을 DB-only fallback으로 실행한다.
     * action 내부 또는 lock 획득 실패 시 발생하는 모든 예외에서 L1 카운터를 복구한다.
     * L3(DB) 롤백은 호출자가 action 내에서 직접 처리한다.
     */
    fun executeWithStockReservation(
        productOptionId: Long,
        action: () -> Unit,
        fallbackAction: () -> Unit,
    ): Boolean {
        var counterReserved = false
        var actionStarted = false

        return try {
            val remaining = stockCounterRepository.decrement(productOptionId)
            if (remaining < 0) {
                throw ErrorException(ErrorType.STOCK_SOLD_OUT)
            }
            counterReserved = true

            // tryLock(leaseTime)은 Redisson watchdog을 비활성화한다.
            // LOCK_LEASE_TIME은 DB 재고 차감 + PENDING 주문 생성 p99 응답 시간 + 버퍼 기준으로 설정한다.
            // 결제 실행은 락 밖에서 처리해 상품 옵션 단위 직렬화 시간을 줄인다.
            distributedLock.executeWithLock(
                key = "lock:booking:$productOptionId",
                waitTime = LOCK_WAIT_TIME,
                leaseTime = LOCK_LEASE_TIME,
            ) {
                actionStarted = true
                action()
                true
            }
        } catch (e: RedisUnavailableException) {
            if (counterReserved) restoreCounter(productOptionId)
            if (actionStarted) throw e
            log.warn(e) { "Redis 장애 감지, DB-only fallback 실행 productOptionId=$productOptionId" }
            fallbackAction()
            false
        } catch (e: Exception) {
            if (counterReserved) restoreCounter(productOptionId)
            throw e
        }
    }

    fun releaseStockReservation(productOptionId: Long) {
        restoreCounter(productOptionId)
    }

    private fun findStockOrThrow(productOptionId: Long): ProductStock =
        productStockRepository.findByProductOptionId(productOptionId)
            ?: throw ErrorException(ErrorType.PRODUCT_NOT_FOUND)

    /**
     * L1 increment 실패는 원래 예외 흐름을 가리지 않고 별도 로그로 남긴다.
     * 복구 실패는 운영 모니터링과 reconciliation 잡으로 보정해야 한다.
     */
    private fun restoreCounter(productOptionId: Long) {
        runCatching {
            stockCounterRepository.increment(productOptionId)
        }.onFailure { ex ->
            log.error(ex) { "L1 카운터 복구 실패 productOptionId=$productOptionId, drift 가능 — reconciliation 필요" }
        }
    }

    companion object {
        private val LOCK_WAIT_TIME = Duration.ofSeconds(1)
        private val LOCK_LEASE_TIME = Duration.ofMillis(5_000)
    }
}
