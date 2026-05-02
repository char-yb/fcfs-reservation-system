package com.reservation.storage.redis.support

import com.reservation.support.redis.RedisUnavailableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.redisson.client.RedisException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RedisCircuitBreakerExceptionAspect {
    @Around("@annotation(io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker) && within(com.reservation.storage.redis..*)")
    fun translateRedisFailure(joinPoint: ProceedingJoinPoint): Any? =
        try {
            joinPoint.proceed()
        } catch (cause: Throwable) {
            throwRedisUnavailable("${joinPoint.signature.toShortString()} failed", cause)
        }

    /**
     * Resilience4j AOP는 순수 Redis 호출 메서드에만 적용한다.
     * DistributedLockProcessor.executeWithLock 전체를 감싸면 action 내부의 결제/주문 예외가
     * Redis circuit 실패로 기록될 수 있으므로 lock lookup/acquire 호출을 별도 bean으로 분리한다.
     */
    private fun throwRedisUnavailable(
        message: String,
        cause: Throwable,
    ): Nothing {
        when (cause) {
            is RedisUnavailableException -> throw cause
            is CallNotPermittedException -> throw RedisUnavailableException("redis circuit open: $message", cause)
            is RedisException -> throw RedisUnavailableException(message, cause)
            is InterruptedException -> {
                Thread.currentThread().interrupt()
                throw cause
            }
            else -> throw cause
        }
    }
}
