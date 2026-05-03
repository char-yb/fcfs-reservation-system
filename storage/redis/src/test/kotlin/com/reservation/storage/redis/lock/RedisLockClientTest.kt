package com.reservation.storage.redis.lock

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class RedisLockClientTest :
    StringSpec({
        val redisContainer =
            GenericContainer(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(REDIS_PORT)
        lateinit var redissonClient: RedissonClient
        lateinit var lockClient: RedisLockClient

        beforeSpec {
            redisContainer.start()
            val config = Config()
            config
                .useSingleServer()
                .setAddress("redis://${redisContainer.host}:${redisContainer.getMappedPort(REDIS_PORT)}")
            redissonClient = Redisson.create(config)
            lockClient = RedisLockClient(redissonClient)
        }

        afterSpec {
            redissonClient.shutdown()
            redisContainer.stop()
        }

        "예약 락은 공정 락을 사용한다" {
            val lock = lockClient.getLock("lock:booking:1")

            lock.javaClass.name shouldContain "RedissonFairLock"
        }
    }) {
    companion object {
        private const val REDIS_PORT = 6379
    }
}
