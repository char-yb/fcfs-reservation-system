package com.reservation.application.fixture

import com.reservation.support.lock.DistributedLock
import java.time.Duration

data class LockCall(
    val key: String,
    val waitTime: Duration,
    val leaseTime: Duration,
)

class RecordingDistributedLock(
    private val failure: RuntimeException? = null,
) : DistributedLock {
    val calls = mutableListOf<LockCall>()

    override fun <T> executeWithLock(
        key: String,
        waitTime: Duration,
        leaseTime: Duration,
        action: () -> T,
    ): T {
        calls.add(LockCall(key = key, waitTime = waitTime, leaseTime = leaseTime))
        failure?.let { throw it }
        return action()
    }
}
