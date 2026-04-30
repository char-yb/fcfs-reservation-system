package com.reservation.support.lock

import java.time.Duration

interface DistributedLock {
    fun <T> executeWithLock(
        key: String,
        waitTime: Duration,
        leaseTime: Duration,
        action: () -> T,
    ): T
}
