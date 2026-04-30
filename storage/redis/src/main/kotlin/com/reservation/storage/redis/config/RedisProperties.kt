package com.reservation.storage.redis.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "redis.reservation")
data class RedisProperties
    @ConstructorBinding
    constructor(
        val host: String = "localhost",
        val port: Int = 6379,
        val commandTimeoutMs: Long = 200,
        val connectTimeoutMs: Long = 1000,
    )
