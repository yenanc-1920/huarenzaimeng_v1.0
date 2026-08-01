package com.huarenzaimeng.api.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@Profile("release-mysql")
class ReleaseMysqlDataSourceConfig {

    @Bean
    DataSource dataSource(
            @Value("${HZ_DATASOURCE_URL}") String jdbcUrl,
            @Value("${SPRING_DATASOURCE_USERNAME}") String username,
            @Value("${SPRING_DATASOURCE_PASSWORD}") String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl.strip());
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(0);
        dataSource.setConnectionTimeout(10_000);
        return dataSource;
    }
}
