package com.reservation.application.payment

import com.reservation.domain.payment.PaymentCommand
import com.reservation.domain.payment.PaymentExecutionResult
import com.reservation.support.error.ErrorException
import com.reservation.support.error.ErrorType
import com.reservation.support.extension.logger
import org.springframework.stereotype.Component

@Component
class PaymentOrchestrator(
    private val registry: PaymentProcessorRegistry,
    private val policy: PaymentCombinationPolicy,
) {
    private val log by logger()

    fun execute(
        commands: List<PaymentCommand>,
        totalAmount: Long,
    ): List<PaymentExecutionResult> {
        policy.validate(commands)
        policy.validateTotal(commands, totalAmount)

        val ordered = orderForExecution(commands)
        val results = mutableListOf<PaymentExecutionResult>()
        try {
            for (command in ordered) {
                results.add(registry.get(command.method).pay(command))
            }
            return results.toList()
        } catch (e: Exception) {
            log.error(e) { "복합 결제 실패, 보상 처리 시작" }
            compensate(results)
            throw ErrorException(ErrorType.PAYMENT_DECLINED)
        }
    }

    private fun compensate(results: List<PaymentExecutionResult>) {
        results.reversed().forEach { result ->
            runCatching {
                registry.get(result.method).cancel(result.transactionId)
            }.onFailure { ex ->
                log.error(ex) { "보상 처리 실패 method=${result.method} txId=${result.transactionId}" }
                // TODO: outbox에 보상 실패 이벤트 기록 (04-fault-tolerance.md 참조)
            }
        }
    }

    private fun orderForExecution(commands: List<PaymentCommand>): List<PaymentCommand> =
        commands.sortedBy { command ->
            when (command.method) {
                com.reservation.domain.payment.PaymentMethod.POINT -> 0
                else -> 1
            }
        }
}
