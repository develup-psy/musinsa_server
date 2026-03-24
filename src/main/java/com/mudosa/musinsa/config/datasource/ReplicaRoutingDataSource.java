package com.mudosa.musinsa.config.datasource;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
public class ReplicaRoutingDataSource extends AbstractRoutingDataSource {

    private final DataSourceRoutingProperties properties;
    private final ReplicaAvailabilityState replicaAvailabilityState;

    @Override
    protected Object determineCurrentLookupKey() {
        if (!properties.getRouting().isEnabled()) {
            return DataSourceRole.MASTER;
        }

        if (!properties.getRouting().isReadOnlyToReplicaEnabled()) {
            return DataSourceRole.MASTER;
        }

        boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();
        boolean readOnlyTx = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (txActive && readOnlyTx && replicaAvailabilityState.isReplicaAvailable()) {
            return DataSourceRole.REPLICA;
        }

        return DataSourceRole.MASTER;
    }
}
