package com.mudosa.musinsa.config.datasource;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ReplicaAvailabilityState {

    private final AtomicBoolean replicaConfigured = new AtomicBoolean(false);
    private final AtomicBoolean replicaAvailable = new AtomicBoolean(false);

    public void markReplicaConfigured(boolean configured) {
        replicaConfigured.set(configured);
        if (!configured) {
            replicaAvailable.set(false);
        }
    }

    public boolean isReplicaConfigured() {
        return replicaConfigured.get();
    }

    public boolean isReplicaAvailable() {
        return replicaConfigured.get() && replicaAvailable.get();
    }

    public void markReplicaHealthy() {
        boolean previous = replicaAvailable.getAndSet(true);
        if (!previous) {
            log.info("[DataSourceRouting] replica datasource is healthy. readOnly traffic can route to replica.");
        }
    }

    public void markReplicaUnhealthy(String reason) {
        boolean previous = replicaAvailable.getAndSet(false);
        if (previous) {
            log.warn("[DataSourceRouting] replica datasource is unhealthy. fallback to master. reason={}", reason);
        }
    }
}
