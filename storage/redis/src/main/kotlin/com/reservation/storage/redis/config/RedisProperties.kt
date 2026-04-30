package com.reservation.storage.redis.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "redis.reservation")
data class RedisProperties(
    val host: String = "localhost",
    val port: Int = 6379,
    val commandTimeoutMs: Long = 200,
    val connectTimeoutMs: Long = 1000,
)
