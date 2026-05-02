package com.reservation.storage.redis.product

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class StockRedisCounterRepositoryTest :
    StringSpec({
        val redisContainer =
            GenericContainer(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(REDIS_PORT)
        lateinit var redissonClient: RedissonClient
        lateinit var repository: StockRedisCounterRepository

        beforeSpec {
            redisContainer.start()
            val config = Config()
            config
                .useSingleServer()
                .setAddress("redis://${redisContainer.host}:${redisContainer.getMappedPort(REDIS_PORT)}")
            redissonClient = Redisson.create(config)
            repository = StockRedisCounterRepository(redissonClient)
        }

        afterSpec {
            redissonClient.shutdown()
            redisContainer.stop()
        }

        "재고 카운터는 0 아래로 내려가지 않는다" {
            redissonClient.getAtomicLong("stock:1").set(1L)

            repository.decrement(1L) shouldBe 0L
            repository.decrement(1L) shouldBe -1L

            redissonClient.getAtomicLong("stock:1").get() shouldBe 0L
        }

        "재고 카운터 키가 없으면 음수 키를 만들지 않는다" {
            repository.decrement(2L) shouldBe -1L

            redissonClient.keys.countExists("stock:2") shouldBe 0L
        }
    }) {
    companion object {
        private const val REDIS_PORT = 6379
    }
}
