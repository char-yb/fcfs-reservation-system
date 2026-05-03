package com.reservation.storage.redis.lock

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class RedisLockClient(
    private val redissonClient: RedissonClient,
) {
    @CircuitBreaker(name = "redis")
    fun getLock(key: String): RLock = redissonClient.getFairLock(key)

    @CircuitBreaker(name = "redis")
    fun tryLock(
        key: String,
        lock: RLock,
        waitTime: Duration,
        leaseTime: Duration,
    ): Boolean =
        lock.tryLock(
            waitTime.toMillis(),
            leaseTime.toMillis(),
            TimeUnit.MILLISECONDS,
        )
}
