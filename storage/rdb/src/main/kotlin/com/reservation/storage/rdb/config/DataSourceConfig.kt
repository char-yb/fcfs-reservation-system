package com.reservation.storage.rdb.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "datasource.reservation")
    fun reservationHikariConfig(): HikariConfig = HikariConfig()

    @Bean
    fun dataSource(reservationHikariConfig: HikariConfig): HikariDataSource =
        HikariDataSource(reservationHikariConfig)
}
