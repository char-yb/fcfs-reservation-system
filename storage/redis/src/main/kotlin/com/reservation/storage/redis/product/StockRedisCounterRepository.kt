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
    override fun decrement(productId: Long): Long = redissonClient.getAtomicLong(stockKey(productId)).decrementAndGet()

    @CircuitBreaker(name = "redis")
    override fun increment(productId: Long) {
        redissonClient.getAtomicLong(stockKey(productId)).incrementAndGet()
    }

    @CircuitBreaker(name = "redis")
    override fun initialize(
        productId: Long,
        quantity: Int,
    ) {
        redissonClient.getAtomicLong(stockKey(productId)).set(quantity.toLong())
    }

    private fun stockKey(productId: Long) = "stock:$productId"
}
