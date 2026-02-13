package com.mudosa.musinsa.payment.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentEventType {

    CREATED("결제 생성"),

    QUEUED("대기열 등록"),

    APPROVAL_REQUESTED("결제 승인 요청"),

    APPROVED("결제 승인 완료"),

    APPROVAL_FAILED("결제 승인 실패"),

    CANCEL_REQUESTED("결제 취소 요청"),

    CANCELLED("결제 취소 완료"),

    CANCEL_FAILED("결제 취소 실패"),

    FAILED("결제 실패"),

    PG_ERROR("PG사 통신 오류"),

    AMOUNT_MISMATCH("결제 금액 불일치"),

    VALIDATION_FAILED("결제 정보 검증 실패"),

    TIMEOUT("결제 타임아웃"),

    REQUIRES_MANUAL_CHECK("수동 확인 필요");

    private final String description;

}
