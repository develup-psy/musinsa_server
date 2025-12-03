package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelRequest;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelResponseDto;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentStrategyFactory strategyFactory;

    @Observed(name = "pg.processPayment", contextualName = "PG-결제-요청")
    public PaymentResponseDto processPayment(PaymentConfirmRequest request) {
        PaymentContext context = PaymentContext.from(request);

        PaymentStrategy strategy = strategyFactory.getStrategy(context);

        return strategy.confirmPayment(request);
    }

    @Observed(name = "pg.processCancelPayment", contextualName = "PG-결제취소-요청")
    public PaymentCancelResponseDto processCancelPayment(PaymentCancelRequest request) {
        PaymentContext context = PaymentContext.forCancel(request);
        PaymentStrategy strategy = strategyFactory.getStrategy(context);
        return strategy.cancelPayment(request);
    }
}
