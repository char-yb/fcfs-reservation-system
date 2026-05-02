package com.reservation.storage.redis.support

import com.reservation.support.redis.RedisUnavailableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
import org.redisson.client.RedisException
import java.lang.reflect.Proxy

class RedisCircuitBreakerExceptionAspectTest :
    StringSpec({
        val aspect = RedisCircuitBreakerExceptionAspect()

        "Redisson 예외를 RedisUnavailableException으로 변환한다" {
            val exception =
                shouldThrow<RedisUnavailableException> {
                    aspect.translateRedisFailure(joinPointThrowing(RedisException("redis down")))
                }

            exception.message shouldContain "RedisCall.decrement() failed"
        }

        "circuit open 예외를 RedisUnavailableException으로 변환한다" {
            val circuitBreaker = CircuitBreaker.ofDefaults("redis")
            circuitBreaker.transitionToOpenState()

            val exception =
                shouldThrow<RedisUnavailableException> {
                    aspect.translateRedisFailure(
                        joinPointThrowing(CallNotPermittedException.createCallNotPermittedException(circuitBreaker)),
                    )
                }

            exception.message shouldContain "redis circuit open"
        }

        "InterruptedException은 interrupted flag를 복구하고 원래 예외를 유지한다" {
            val exception = InterruptedException("interrupted")

            val thrown =
                shouldThrow<InterruptedException> {
                    aspect.translateRedisFailure(joinPointThrowing(exception))
                }

            thrown shouldBe exception
            Thread.interrupted() shouldBe true
        }
    })

fun joinPointThrowing(cause: Throwable): ProceedingJoinPoint {
    val signature = redisCallSignature()
    return Proxy.newProxyInstance(
        ProceedingJoinPoint::class.java.classLoader,
        arrayOf(ProceedingJoinPoint::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "proceed" -> throw cause
            "getSignature" -> signature
            "toShortString", "toString" -> "RedisCall.decrement()"
            else -> null
        }
    } as ProceedingJoinPoint
}

fun redisCallSignature(): Signature =
    Proxy.newProxyInstance(
        Signature::class.java.classLoader,
        arrayOf(Signature::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "toShortString", "toLongString", "toString" -> "RedisCall.decrement()"
            "getName" -> "decrement"
            "getModifiers" -> 0
            "getDeclaringType" -> Any::class.java
            "getDeclaringTypeName" -> "RedisCall"
            else -> null
        }
    } as Signature
