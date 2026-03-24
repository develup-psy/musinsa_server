package com.mudosa.musinsa.config.datasource;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(name = "replicaDataSource")
@ConditionalOnProperty(prefix = "app.datasource.routing", name = "enabled", havingValue = "true")
public class ReplicaHealthCheckScheduler {

    @Qualifier("replicaDataSource")
    private final DataSource replicaDataSource;
    private final DataSourceRoutingProperties properties;
    private final ReplicaAvailabilityState replicaAvailabilityState;

    @PostConstruct
    public void checkOnStartup() {
        checkReplica();
    }

    @Scheduled(fixedDelayString = "${app.datasource.routing.replica-health-check-interval-millis:5000}")
    public void scheduledCheck() {
        checkReplica();
    }

    private void checkReplica() {
        try (Connection connection = replicaDataSource.getConnection()) {
            boolean valid = connection.isValid(properties.getRouting().getReplicaValidationTimeoutSeconds());
            if (valid) {
                replicaAvailabilityState.markReplicaHealthy();
            } else {
                replicaAvailabilityState.markReplicaUnhealthy("validation_failed");
            }
        } catch (Exception e) {
            replicaAvailabilityState.markReplicaUnhealthy(e.getClass().getSimpleName());
            log.debug("[DataSourceRouting] replica health check failed", e);
        }
    }
}
