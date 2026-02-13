package com.mudosa.musinsa.payment.domain.model;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    QUEUED("대기열 대기") {
        @Override
        public PaymentStatus approve() {
            return APPROVED;
        }

        @Override
        public PaymentStatus fail() {
            return FAILED;
        }

        @Override
        public PaymentStatus cancel() {
            throw invalidTransition("취소");
        }

        @Override
        public PaymentStatus toPending() {
            return PENDING;
        }
    },

    PENDING("결제 대기") {
        @Override
        public PaymentStatus approve() {
            return APPROVED;
        }

        @Override
        public PaymentStatus fail() {
            return FAILED;
        }

        @Override
        public PaymentStatus cancel() {
            throw invalidTransition("취소");
        }
    },

    APPROVED("결제 승인") {
        @Override
        public PaymentStatus approve() {
            throw invalidTransition("승인");
        }

        @Override
        public PaymentStatus fail() {
            throw invalidTransition("실패");
        }

        @Override
        public PaymentStatus cancel() {
            return CANCELLED;
        }

    },

    FAILED("결제 실패") {
        @Override
        public PaymentStatus approve() {
            throw invalidTransition("승인");
        }

        @Override
        public PaymentStatus fail() {
            throw invalidTransition("실패");
        }

        @Override
        public PaymentStatus cancel() {
            throw invalidTransition("취소");
        }

        @Override
        public PaymentStatus rollback() {
            return PENDING;
        }
    },

    CANCELLED("결제 취소") {
        @Override
        public PaymentStatus approve() {
            throw invalidTransition("승인");
        }

        @Override
        public PaymentStatus fail() {
            throw invalidTransition("실패");
        }

        @Override
        public PaymentStatus cancel() {
            throw invalidTransition("취소");
        }

        @Override
        public PaymentStatus rollback() {
            return APPROVED;
        }
    };

    private final String description;

    public abstract PaymentStatus approve();
    public abstract PaymentStatus fail();
    public abstract PaymentStatus cancel();

    public PaymentStatus toPending() {
        throw invalidTransition("대기 전환");
    }

    public PaymentStatus rollback() {
        throw invalidTransition("재시도");
    }

    protected BusinessException invalidTransition(String action) {
        return new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS,
                String.format("%s 상태에서는 %s할 수 없습니다", this.description, action)
        );
    }
}
