package com.reservation.storage.redis.lock

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import com.reservation.support.lock.DistributedLock
import org.redisson.api.RLock
import org.redisson.client.RedisException
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class DistributedLockProcessor(
    private val redisLockClient: RedisLockClient,
) : DistributedLock {
    private val log by logger()

    override fun <T> executeWithLock(
        key: String,
        waitTime: Duration,
        leaseTime: Duration,
        action: () -> T,
    ): T {
        val lock = redisLockClient.getLock(key)
        val acquired = redisLockClient.tryLock(key, lock, waitTime, leaseTime)
        if (!acquired) throw ErrorException(ErrorType.LOCK_ACQUISITION_FAILED)
        try {
            return action()
        } finally {
            releaseLock(key, lock)
        }
    }

    private fun releaseLock(
        key: String,
        lock: RLock,
    ) {
        try {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        } catch (e: RedisException) {
            log.warn(e) { "분산락 해제 실패 key=$key, lease 만료 대기" }
        }
    }
}
