package com.reservation.application.user

import com.reservation.domain.user.UserPoint
import com.reservation.domain.user.UserPointRepository
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class UserPointService(
    private val userPointRepository: UserPointRepository,
) {
    @Transactional(readOnly = true)
    fun getPoint(userId: Long): UserPoint =
        userPointRepository.findByUserId(userId)
            ?: throw ErrorException(ErrorType.USER_NOT_FOUND)

    @Transactional
    fun charge(
        userId: Long,
        amount: Long,
    ): UserPoint {
        val userPoint =
            userPointRepository.findByUserId(userId)
                ?: UserPoint(userId = userId, pointBalance = 0L)
        val charged = userPoint.copy(pointBalance = userPoint.pointBalance + amount)
        return userPointRepository.save(charged)
    }

    @Transactional
    fun deduct(
        userId: Long,
        amount: Long,
    ): String {
        val userPoint =
            userPointRepository.findByUserId(userId)
                ?: throw ErrorException(ErrorType.USER_NOT_FOUND)
        val deducted = userPoint.deduct(amount)
        userPointRepository.save(deducted)
        return "pt_${userId}_${amount}_${UUID.randomUUID()}"
    }

    @Transactional
    fun refund(transactionId: String) {
        val parts = transactionId.split("_")
        val userId = parts[1].toLong()
        val amount = parts[2].toLong()
        val userPoint =
            userPointRepository.findByUserId(userId)
                ?: throw ErrorException(ErrorType.USER_NOT_FOUND)
        userPointRepository.save(userPoint.refund(amount))
    }
}
