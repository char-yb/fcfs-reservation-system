package com.reservation.api.exception

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.redis.RedisUnavailableException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GlobalExceptionHandlerTest :
    StringSpec({
        "Redis 장애 예외는 503 응답으로 변환한다" {
            val handler = GlobalExceptionHandler()

            val response = handler.handleRedisUnavailableException(RedisUnavailableException("redis down"))

            response.statusCode.value() shouldBe 503
            response.body?.success shouldBe false
            response.body?.status shouldBe 503
            response.body?.data?.code shouldBe ErrorType.REDIS_UNAVAILABLE.name
            response.body?.data?.message shouldBe ErrorType.REDIS_UNAVAILABLE.message
        }

        "재고 소진 예외는 409 응답으로 변환한다" {
            val handler = GlobalExceptionHandler()

            val response = handler.handleErrorException(ErrorException(ErrorType.STOCK_SOLD_OUT))

            response.statusCode.value() shouldBe 409
            response.body?.success shouldBe false
            response.body?.status shouldBe 409
            response.body?.data?.code shouldBe ErrorType.STOCK_SOLD_OUT.name
            response.body?.data?.message shouldBe ErrorType.STOCK_SOLD_OUT.message
        }
    })
