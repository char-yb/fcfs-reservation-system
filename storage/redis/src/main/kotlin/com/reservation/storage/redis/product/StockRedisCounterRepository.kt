package com.reservation.storage.redis.product

import com.reservation.domain.product.StockCounterRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository

@Repository
class StockRedisCounterRepository(
    private val redissonClient: RedissonClient,
) : StockCounterRepository {
    @CircuitBreaker(name = "redis")
    override fun decrement(productOptionId: Long): Long = redissonClient.getAtomicLong(stockKey(productOptionId)).decrementAndGet()

    @CircuitBreaker(name = "redis")
    override fun increment(productOptionId: Long) {
        redissonClient.getAtomicLong(stockKey(productOptionId)).incrementAndGet()
    }

    @CircuitBreaker(name = "redis")
    override fun initialize(
        productOptionId: Long,
        quantity: Int,
    ) {
        redissonClient.getAtomicLong(stockKey(productOptionId)).set(quantity.toLong())
    }

    private fun stockKey(productOptionId: Long) = "stock:$productOptionId"
}
