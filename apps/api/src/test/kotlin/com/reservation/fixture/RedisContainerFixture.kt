package com.reservation.fixture

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

object RedisContainerFixture {
    private const val REDIS_PORT = 6379

    private val redisContainer =
        GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT)

    fun registerProperties(registry: DynamicPropertyRegistry) {
        redisContainer.start()

        registry.add("redis.reservation.host") { redisContainer.host }
        registry.add("redis.reservation.port") { redisContainer.getMappedPort(REDIS_PORT) }
    }
}
