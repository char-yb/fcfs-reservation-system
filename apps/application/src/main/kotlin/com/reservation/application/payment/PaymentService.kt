package com.reservation.application.payment

import com.reservation.domain.outbox.CompensationFailurePayload
import com.reservation.domain.outbox.OutboxEvent
import com.reservation.domain.outbox.OutboxEventRepository
import com.reservation.domain.outbox.OutboxEventType
import com.reservation.domain.payment.Payment
import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.domain.payment.PaymentRepository
import com.reservation.domain.payment.PaymentStatus
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val strategyRegistry: PaymentStrategyRegistry,
    private val validator: PaymentValidator,
    private val paymentRepository: PaymentRepository,
    private val outboxEventRepository: OutboxEventRepository,
) {
    private val log by logger()

    /*
     * 결제 실행은 외부 PG 호출과 포인트 차감을 포함한다.
     * 상위 DB 트랜잭션에 묶이면 원격 호출 시간만큼 커넥션과 락을 점유하므로 기존 트랜잭션을 중단한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun execute(
        commands: List<PaymentCommand>,
        totalAmount: Long,
        orderId: Long? = null,
    ): List<PaymentExecutionResult> {
        validator.validate(commands)
        validator.validateTotal(commands, totalAmount)

        val ordered =
            commands.sortedBy { command ->
                when (command.method) {
                    PaymentMethod.Y_POINT -> 0
                    else -> 1
                }
            }
        val results = mutableListOf<PaymentExecutionResult>()
        try {
            for (command in ordered) {
                results.add(strategyRegistry.get(command.method).pay(command))
            }
            return results.toList()
        } catch (e: ErrorException) {
            // 정의된 비즈니스 오류는 errorType과 logging level을 보존해 GlobalExceptionHandler에 전달한다.
            log.warn(e) { "복합 결제 실패 (정의된 오류) errorType=${e.errorType}, 보상 처리 시작" }
            compensate(orderId, results)
            throw e
        } catch (e: Exception) {
            log.error(e) { "복합 결제 실패 (예기치 못한 오류), 보상 처리 시작" }
            compensate(orderId, results)
            throw ErrorException(ErrorType.PAYMENT_DECLINED)
        }
    }

    /*
     * 보상은 best-effort 취소 루프이므로 전체를 하나의 DB 트랜잭션으로 묶지 않는다.
     * 포인트 환불, 결제 상태 변경, outbox 저장처럼 DB가 필요한 작업만 각자 짧은 트랜잭션에서 처리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun compensate(
        orderId: Long?,
        results: List<PaymentExecutionResult>,
    ): List<PaymentExecutionResult> {
        val failures = mutableListOf<PaymentExecutionResult>()
        results.reversed().forEach { result ->
            runCatching {
                strategyRegistry.get(result.method).cancel(result.transactionId)
            }.onFailure { ex ->
                failures.add(result)
                log.error(ex) { "보상 처리 실패 method=${result.method} txId=${result.transactionId}" }
                if (orderId != null) saveCompensationFailure(orderId, result, ex)
            }
        }
        return failures
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveApproved(
        orderId: Long,
        results: List<PaymentExecutionResult>,
    ): List<Payment> =
        paymentRepository.saveAll(
            results.map { result ->
                Payment(
                    id = 0L,
                    orderId = orderId,
                    method = result.method,
                    amount = result.amount,
                    status = PaymentStatus.APPROVED,
                    pgTransactionId = result.transactionId,
                    externalRequestId = null,
                )
            },
        )

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markCancelled(
        orderId: Long,
        results: List<PaymentExecutionResult>,
    ): Int =
        paymentRepository.updateStatusByOrderIdAndTransactionIds(
            orderId = orderId,
            transactionIds = results.map { it.transactionId },
            status = PaymentStatus.CANCELLED,
        )

    private fun saveCompensationFailure(
        orderId: Long,
        result: PaymentExecutionResult,
        cause: Throwable,
    ) {
        runCatching {
            outboxEventRepository.save(
                OutboxEvent(
                    orderId = orderId,
                    eventType = OutboxEventType.COMPENSATION_FAILURE,
                    payload = compensationFailurePayload(orderId, result, cause),
                ),
            )
        }.onFailure { outboxFailure ->
            log.error(outboxFailure) { "보상 실패 outbox 저장 실패 orderId=$orderId txId=${result.transactionId}" }
        }
    }

    private fun compensationFailurePayload(
        orderId: Long,
        result: PaymentExecutionResult,
        cause: Throwable,
    ): CompensationFailurePayload =
        CompensationFailurePayload(
            orderId = orderId,
            method = result.method,
            amount = result.amount,
            pgTransactionId = result.transactionId,
            reason = cause.message ?: cause.javaClass.simpleName,
        )
}
