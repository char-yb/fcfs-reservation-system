package com.reservation.support.redis

class RedisUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
