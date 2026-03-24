package com.mudosa.musinsa.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(DataSourceRoutingProperties.class)
public class DataSourceRoutingConfig {

    @Bean(name = "masterDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource masterDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "replicaDataSource")
    @ConditionalOnExpression(
            "${app.datasource.replica.enabled:false} and '${app.datasource.replica.url:}' != ''"
    )
    public HikariDataSource replicaDataSource(DataSourceRoutingProperties properties) {
        DataSourceRoutingProperties.Replica replica = properties.getReplica();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(replica.getDriverClassName());
        dataSource.setJdbcUrl(replica.getUrl());
        dataSource.setUsername(replica.getUsername());
        dataSource.setPassword(replica.getPassword());
        dataSource.setMaximumPoolSize(replica.getHikari().getMaximumPoolSize());
        dataSource.setMinimumIdle(replica.getHikari().getMinimumIdle());
        dataSource.setConnectionTimeout(replica.getHikari().getConnectionTimeout());
        dataSource.setMaxLifetime(replica.getHikari().getMaxLifetime());
        dataSource.setKeepaliveTime(replica.getHikari().getKeepaliveTime());
        return dataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("replicaDataSource") ObjectProvider<DataSource> replicaDataSourceProvider,
            DataSourceRoutingProperties properties,
            ReplicaAvailabilityState replicaAvailabilityState
    ) {
        DataSource replicaDataSource = replicaDataSourceProvider.getIfAvailable();
        boolean replicaConfigured = replicaDataSource != null;
        replicaAvailabilityState.markReplicaConfigured(replicaConfigured);

        if (!properties.getRouting().isEnabled()) {
            log.info("[DataSourceRouting] disabled. all traffic routes to master.");
            return masterDataSource;
        }

        if (!replicaConfigured) {
            log.warn("[DataSourceRouting] enabled but replica is not configured. fallback to master only.");
            return masterDataSource;
        }

        ReplicaRoutingDataSource routingDataSource = new ReplicaRoutingDataSource(properties, replicaAvailabilityState);
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceRole.MASTER, masterDataSource);
        targets.put(DataSourceRole.REPLICA, replicaDataSource);

        routingDataSource.setTargetDataSources(targets);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.afterPropertiesSet();

        log.info("[DataSourceRouting] enabled. readOnly tx can route to replica when healthy.");
        return routingDataSource;
    }
}
