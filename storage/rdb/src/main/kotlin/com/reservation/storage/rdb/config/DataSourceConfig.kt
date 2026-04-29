package com.reservation.storage.rdb.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DataSourceConfig {
    @Bean
    @ConfigurationProperties(prefix = "datasource.reservation")
    fun reservationHikariConfig(): HikariConfig = HikariConfig()

    @Bean
    fun coreDataSource(
        @Qualifier("reservationHikariConfig") config: HikariConfig,
    ): HikariDataSource = HikariDataSource(config)
}
