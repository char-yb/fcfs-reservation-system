package com.reservation.application.user

import com.reservation.application.fixture.FakeUserPointRepository
import com.reservation.domain.user.UserPoint
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UserPointServiceTest :
    StringSpec({
        "사용자 포인트를 조회한다" {
            val repository = FakeUserPointRepository(listOf(UserPoint(userId = 1L, pointBalance = 10_000L)))
            val service = UserPointService(repository)

            service.getPoint(1L).pointBalance shouldBe 10_000L
        }

        "사용자 포인트가 없으면 조회를 거부한다" {
            val service = UserPointService(FakeUserPointRepository())

            val exception =
                shouldThrow<ErrorException> {
                    service.getPoint(1L)
                }

            exception.errorType shouldBe ErrorType.USER_NOT_FOUND
        }

        "포인트를 충전한다" {
            val repository = FakeUserPointRepository(listOf(UserPoint(userId = 1L, pointBalance = 10_000L)))
            val service = UserPointService(repository)

            service.charge(userId = 1L, amount = 5_000L).pointBalance shouldBe 15_000L
        }

        "포인트가 없던 사용자도 충전하면 잔액을 생성한다" {
            val repository = FakeUserPointRepository()
            val service = UserPointService(repository)

            service.charge(userId = 1L, amount = 5_000L).pointBalance shouldBe 5_000L
        }

        "포인트를 차감하고 거래 식별자를 반환한다" {
            val repository = FakeUserPointRepository(listOf(UserPoint(userId = 1L, pointBalance = 10_000L)))
            val service = UserPointService(repository)

            val transactionId = service.deduct(userId = 1L, amount = 7_000L)

            transactionId.startsWith("pt_1_7000_") shouldBe true
            repository.points[1L]?.pointBalance shouldBe 3_000L
        }

        "포인트 차감 대상 사용자가 없으면 거부한다" {
            val service = UserPointService(FakeUserPointRepository())

            val exception =
                shouldThrow<ErrorException> {
                    service.deduct(userId = 1L, amount = 7_000L)
                }

            exception.errorType shouldBe ErrorType.USER_NOT_FOUND
        }

        "포인트 거래 식별자로 환불한다" {
            val repository = FakeUserPointRepository(listOf(UserPoint(userId = 1L, pointBalance = 3_000L)))
            val service = UserPointService(repository)

            service.refund("pt_1_7000_uuid")

            repository.points[1L]?.pointBalance shouldBe 10_000L
        }
    })
