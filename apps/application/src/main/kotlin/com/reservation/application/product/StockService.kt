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
     * L1(Redis counter) + L2(distributed lock) 재고 보호 실행.
     * Redis adapter가 장애 또는 circuit open 상태를 RedisUnavailableException으로 변환하면
     * L1/L2를 건너뛰고 action을 DB-only fallback으로 실행한다.
     * action 내부 또는 lock 획득 실패 시 발생하는 모든 예외에서 L1 카운터를 복구한다.
     * L3(DB) 롤백은 호출자가 action 내에서 직접 처리한다.
     */
    fun <T> executeWithStockGuard(
        productOptionId: Long,
        action: () -> T,
    ): T = executeWithStockGuard(productOptionId = productOptionId, action = action, fallbackAction = action)

    fun <T> executeWithStockGuard(
        productOptionId: Long,
        action: () -> T,
        fallbackAction: () -> T,
    ): T {
        var counterReserved = false
        var actionStarted = false
        var redisFailureBeforeAction = false

        return try {
            executeWithRedisGate(
                productOptionId = productOptionId,
                action = {
                    actionStarted = true
                    action()
                },
                onCounterReserved = { counterReserved = true },
                onRedisFailureBeforeAction = { redisFailureBeforeAction = !actionStarted },
            )
        } catch (e: RedisUnavailableException) {
            if (!redisFailureBeforeAction && counterReserved) throw e
            log.warn(e) { "Redis 장애 감지, DB-only fallback 실행 productOptionId=$productOptionId" }
            fallbackAction()
        }
    }

    private fun findStockOrThrow(productOptionId: Long): ProductStock =
        productStockRepository.findByProductOptionId(productOptionId)
            ?: throw ErrorException(ErrorType.PRODUCT_NOT_FOUND)

    private fun <T> executeWithRedisGate(
        productOptionId: Long,
        action: () -> T,
        onCounterReserved: () -> Unit,
        onRedisFailureBeforeAction: () -> Unit,
    ): T {
        val remaining = stockCounterRepository.decrement(productOptionId)
        onCounterReserved()
        if (remaining < 0) {
            restoreCounter(productOptionId)
            throw ErrorException(ErrorType.STOCK_SOLD_OUT)
        }

        // tryLock(leaseTime)은 Redisson watchdog을 비활성화한다.
        // LOCK_LEASE_TIME은 결제 PG p99 응답 시간 + 버퍼 기준으로 설정한다.
        // 어플리케이션 레벨 결제 timeout은 leaseTime보다 짧게 유지해야 한다.
        try {
            return distributedLock.executeWithLock(
                key = "lock:booking:$productOptionId",
                waitTime = LOCK_WAIT_TIME,
                leaseTime = LOCK_LEASE_TIME,
                action = action,
            )
        } catch (e: RedisUnavailableException) {
            onRedisFailureBeforeAction()
            restoreCounter(productOptionId)
            throw e
        } catch (e: Exception) {
            restoreCounter(productOptionId)
            throw e
        }
    }

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
        private val LOCK_WAIT_TIME = Duration.ofMillis(100)
        private val LOCK_LEASE_TIME = Duration.ofMillis(5_000)
    }
}
