package com.mudosa.musinsa.payment.application.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnqueueResult {

    private final String status;
    private final long rank;
    private final long total;

    public boolean isQueued() {
        return "QUEUED".equals(status);
    }

    public boolean isRejected() {
        return "REJECTED".equals(status);
    }

    public int estimateWaitSeconds(int pgTps) {
        if (rank <= 0) return 0;
        return (int) (rank / pgTps) + 1;
    }
}
