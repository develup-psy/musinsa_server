package com.mudosa.musinsa.payment.application.service;

import com.mudosa.musinsa.payment.application.dto.request.*;
import com.mudosa.musinsa.payment.application.dto.PaymentResponseDto;
import com.mudosa.musinsa.payment.application.dto.response.TossPaymentCancelResponse;
import com.mudosa.musinsa.payment.application.dto.response.TossPaymentConfirmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "payment.pg-provider", havingValue = "toss")
@RequiredArgsConstructor
public class TossPaymentStrategy implements PaymentStrategy {

	private final TossPaymentService tossPaymentService;

	@Override
	public PaymentResponseDto confirmPayment(PaymentConfirmRequest request) {
		TossPaymentConfirmRequest tossRequest = request.toTossRequest();
		TossPaymentConfirmResponse tossResponse = tossPaymentService.callTossApi(tossRequest);
		return PaymentResponseDto.from(tossResponse);
	}

	@Override
	public PaymentCancelResponseDto cancelPayment(PaymentCancelRequest request) {
		TossPaymentCancelRequest tossRequest = TossPaymentCancelRequest.toTossCancelRequest(request);
		TossPaymentCancelResponse tossResponse = tossPaymentService.callTossCancelApi(tossRequest);
		return PaymentCancelResponseDto.from(tossResponse);
	}
}
