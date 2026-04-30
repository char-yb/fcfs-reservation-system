package com.reservation.application.product

import com.reservation.domain.product.StockCounterRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import com.reservation.support.lock.DistributedLock
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class StockLockService(
    private val stockCounterRepository: StockCounterRepository,
    private val distributedLock: DistributedLock,
) {
    private val log by logger()

    /**
     * L1(Redis counter) + L2(distributed lock) 재고 보호 실행.
     * action 내부 또는 lock 획득 실패 시 발생하는 모든 예외에서 L1 카운터를 복구한다.
     * L3(DB) 롤백은 호출자가 action 내에서 직접 처리한다.
     */
    fun <T> executeWithStockLock(
        productId: Long,
        action: () -> T,
    ): T {
        val remaining = stockCounterRepository.decrement(productId)
        if (remaining < 0) {
            restoreCounter(productId)
            throw ErrorException(ErrorType.STOCK_SOLD_OUT)
        }

        // tryLock(leaseTime)은 Redisson watchdog을 비활성화한다.
        // LOCK_LEASE_TIME은 결제 PG p99 응답 시간 + 버퍼 기준으로 설정한다.
        // 어플리케이션 레벨 결제 timeout은 leaseTime보다 짧게 유지해야 한다.
        return try {
            distributedLock.executeWithLock(
                key = "lock:booking:$productId",
                waitTime = LOCK_WAIT_TIME,
                leaseTime = LOCK_LEASE_TIME,
                action = action,
            )
        } catch (e: Exception) {
            restoreCounter(productId)
            throw e
        }
    }

    /**
     * L1 increment 실패는 원래 예외 흐름을 가리지 않고 별도 로그로 남긴다.
     * 복구 실패는 운영 모니터링과 reconciliation 잡으로 보정해야 한다.
     */
    private fun restoreCounter(productId: Long) {
        runCatching {
            stockCounterRepository.increment(productId)
        }.onFailure { ex ->
            log.error(ex) { "L1 카운터 복구 실패 productId=$productId, drift 가능 — reconciliation 필요" }
        }
    }

    companion object {
        private val LOCK_WAIT_TIME = Duration.ofMillis(100)
        private val LOCK_LEASE_TIME = Duration.ofMillis(5_000)
    }
}
