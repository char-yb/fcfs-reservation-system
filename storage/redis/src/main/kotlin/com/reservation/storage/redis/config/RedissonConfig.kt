package com.reservation.storage.redis.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedissonConfig {
    @Bean(destroyMethod = "shutdown")
    fun redissonClient(props: RedisProperties): RedissonClient {
        val config = Config()
        config
            .useSingleServer()
            .setAddress("redis://${props.host}:${props.port}")
            .setTimeout(props.commandTimeoutMs.toInt())
            .setConnectTimeout(props.connectTimeoutMs.toInt())
        return Redisson.create(config)
    }
}
