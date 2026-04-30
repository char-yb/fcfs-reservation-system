package com.reservation.domain.user

import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UserPointTest :
    StringSpec({
        "포인트를 차감하면 잔액이 감소한다" {
            val userPoint = UserPoint(userId = 1L, pointBalance = 50_000L)

            userPoint.deduct(30_000L).pointBalance shouldBe 20_000L
        }

        "잔액보다 큰 포인트 차감은 거부한다" {
            val userPoint = UserPoint(userId = 1L, pointBalance = 10_000L)

            val exception =
                shouldThrow<ErrorException> {
                    userPoint.deduct(30_000L)
                }

            exception.errorType shouldBe ErrorType.INSUFFICIENT_POINT
        }

        "포인트를 환불하면 잔액이 증가한다" {
            val userPoint = UserPoint(userId = 1L, pointBalance = 10_000L)

            userPoint.refund(30_000L).pointBalance shouldBe 40_000L
        }
    })
