package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.payment.config.PaymentConfirmExecutionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmExecutionService {

    @Qualifier("paymentConfirmExecutor")
    private final ExecutorService paymentConfirmExecutor;

    @Qualifier("paymentConfirmSemaphore")
    private final Semaphore paymentConfirmSemaphore;

    private final PaymentConfirmExecutionProperties properties;

    public <T> CompletableFuture<T> execute(Supplier<T> task) {
        if (!properties.isEnabled()) {
            return runAsync(task, false);
        }

        if (!properties.getBulkhead().isEnabled()) {
            return runAsync(task, false);
        }

        boolean acquired;

        try {
            acquired = paymentConfirmSemaphore.tryAcquire(
                    properties.getBulkhead().getAcquireTimeoutMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failedFuture(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "결제 처리 대기 중 인터럽트가 발생했습니다."));
        }

        if (!acquired) {
            log.warn("[PaymentConfirmExecution] bulkhead full - availablePermits={}", paymentConfirmSemaphore.availablePermits());
            return failedFuture(new BusinessException(ErrorCode.PG_RATE_LIMIT_EXCEEDED));
        }

        return runAsync(task, true);
    }

    private <T> CompletableFuture<T> runAsync(Supplier<T> task, boolean releasePermit) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            paymentConfirmExecutor.execute(() -> {
                try {
                    future.complete(task.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                } finally {
                    if (releasePermit) {
                        paymentConfirmSemaphore.release();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (releasePermit) {
                paymentConfirmSemaphore.release();
            }
            future.completeExceptionally(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "결제 실행기가 요청을 수용하지 못했습니다."));
        }
        return future;
    }

    private <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
