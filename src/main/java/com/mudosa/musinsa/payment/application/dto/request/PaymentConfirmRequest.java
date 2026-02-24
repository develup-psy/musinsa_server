package com.mudosa.musinsa.payment.application.dto.request;

import com.mudosa.musinsa.payment.application.dto.PaymentCreateDto;
import com.mudosa.musinsa.payment.domain.model.PgProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {

    @NotBlank(message = "결제 키는 필수입니다")
    private String paymentKey;

    @NotNull(message = "주문 ID는 필수입니다")
    private String orderNo;

    @NotNull(message = "결제 금액은 필수입니다")
    private Long amount;

    @Builder.Default
    private PgProvider pgProvider = PgProvider.TOSS;

    public TossPaymentConfirmRequest toTossRequest() {
        return TossPaymentConfirmRequest.builder()
                .amount(amount)
                .orderId(orderNo)
                .paymentKey(paymentKey)
                .build();
    }

    public PaymentCreateDto toPaymentCreateRequest(){
        return PaymentCreateDto.builder()
                .totalAmount(new BigDecimal(amount))
                .orderNo(orderNo)
                .pgProvider(pgProvider)
                .build();
    }
}
