package com.reservation.application.payment

import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.domain.payment.PaymentMethod
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val strategyRegistry: PaymentStrategyRegistry,
    private val validator: PaymentValidator,
) {
    private val log by logger()

    fun execute(
        commands: List<PaymentCommand>,
        totalAmount: Long,
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
            compensate(results)
            throw e
        } catch (e: Exception) {
            log.error(e) { "복합 결제 실패 (예기치 못한 오류), 보상 처리 시작" }
            compensate(results)
            throw ErrorException(ErrorType.PAYMENT_DECLINED)
        }
    }

    fun compensate(results: List<PaymentExecutionResult>) {
        results.reversed().forEach { result ->
            runCatching {
                strategyRegistry.get(result.method).cancel(result.transactionId)
            }.onFailure { ex ->
                log.error(ex) { "보상 처리 실패 method=${result.method} txId=${result.transactionId}" }
                // TODO: outbox에 보상 실패 이벤트 기록 (04-fault-tolerance.md 참조)
            }
        }
    }
}
