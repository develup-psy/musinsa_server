package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.order.application.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompensationService {

    private final OrderService orderService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollbackOrderAfterQueuedCreationFailure(Long orderId) {
        orderService.rollbackOrder(orderId);
        log.warn("[PaymentCompensation] queued 결제 생성 실패 보상 완료: orderId={}", orderId);
    }
}
