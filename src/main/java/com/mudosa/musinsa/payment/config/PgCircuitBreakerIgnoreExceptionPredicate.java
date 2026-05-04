package com.mudosa.musinsa.payment.config;

import java.util.function.Predicate;

public class PgCircuitBreakerIgnoreExceptionPredicate implements Predicate<Throwable> {

    private static final PgCircuitBreakerRecordFailurePredicate RECORD_FAILURE_PREDICATE =
            new PgCircuitBreakerRecordFailurePredicate();

    @Override
    public boolean test(Throwable throwable) {
        return !RECORD_FAILURE_PREDICATE.test(throwable);
    }
}
