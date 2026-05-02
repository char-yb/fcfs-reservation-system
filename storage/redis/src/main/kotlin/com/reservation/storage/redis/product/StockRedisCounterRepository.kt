package com.reservation.storage.redis.product

import com.reservation.domain.product.StockCounterRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.stereotype.Repository

@Repository
class StockRedisCounterRepository(
    private val redissonClient: RedissonClient,
) : StockCounterRepository {
    @CircuitBreaker(name = "redis")
    override fun decrement(productOptionId: Long): Long =
        redissonClient
            .getScript(StringCodec.INSTANCE)
            .eval(
                RScript.Mode.READ_WRITE,
                DECREMENT_IF_POSITIVE_SCRIPT,
                RScript.ReturnType.INTEGER,
                listOf(STOCK_KEY_PREFIX + productOptionId),
            )

    override fun increment(productOptionId: Long) {
        redissonClient.getAtomicLong(STOCK_KEY_PREFIX + productOptionId).incrementAndGet()
    }

    @CircuitBreaker(name = "redis")
    override fun initialize(
        productOptionId: Long,
        quantity: Int,
    ) {
        redissonClient.getAtomicLong(STOCK_KEY_PREFIX + productOptionId).set(quantity.toLong())
    }

    companion object {
        private const val STOCK_KEY_PREFIX = "stock:"
        private const val DECREMENT_IF_POSITIVE_SCRIPT =
            """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current <= 0 then
                return -1
            end
            return redis.call('DECR', KEYS[1])
            """
    }
}
