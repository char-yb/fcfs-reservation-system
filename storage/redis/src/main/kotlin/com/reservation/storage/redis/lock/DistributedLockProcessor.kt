package com.reservation.storage.redis.lock

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.lock.DistributedLock
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class DistributedLockProcessor(
    private val redissonClient: RedissonClient,
) : DistributedLock {
    override fun <T> executeWithLock(
        key: String,
        waitTime: Duration,
        leaseTime: Duration,
        action: () -> T,
    ): T {
        val lock = redissonClient.getLock(key)
        val acquired =
            lock.tryLock(
                waitTime.toMillis(),
                leaseTime.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        if (!acquired) throw ErrorException(ErrorType.LOCK_ACQUISITION_FAILED)
        try {
            return action()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }
}
