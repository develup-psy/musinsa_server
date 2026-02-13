package com.mudosa.musinsa.payment.controller;

import com.mudosa.musinsa.common.dto.ApiResponse;
import com.mudosa.musinsa.payment.application.dto.request.PaymentCancelRequest;
import com.mudosa.musinsa.payment.application.dto.request.PaymentConfirmRequest;
import com.mudosa.musinsa.payment.application.dto.response.PaymentCancelResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentConfirmResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentQueueResponse;
import com.mudosa.musinsa.payment.application.dto.response.PaymentStatusResponse;
import com.mudosa.musinsa.payment.application.service.PaymentService;
import com.mudosa.musinsa.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	@Operation(
			summary = "결제 승인",
			description = "결제 승인 요청")
	@PostMapping("/confirm")
	public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirmPayment(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@Valid @RequestBody PaymentConfirmRequest request) {

		Long userId = userDetails.getUserId();
		PaymentConfirmResponse response = paymentService.confirmPayment(request, userId);

		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(
			summary = "결제 취소",
			description = "결제를 취소 합니다.")
	@PutMapping("/cancel")
	public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancelOrder(
			@RequestBody PaymentCancelRequest request,
			@AuthenticationPrincipal CustomUserDetails userDetails) {

		Long userId = userDetails.getUserId();
		LocalDateTime cancelledAt = LocalDateTime.now();

		PaymentCancelResponse response = paymentService.cancelPayment(request, userId, cancelledAt);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@Operation(
			summary = "결제 승인 (대기열 기반)",
			description = "대기열 기반 비동기 결제 승인 요청. 대기 순번 및 예상 시간 포함")
	@PostMapping("/confirm/queue")
	public ResponseEntity<ApiResponse<PaymentQueueResponse>> confirmPaymentAsync(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@Valid @RequestBody PaymentConfirmRequest request) {

		Long userId = userDetails.getUserId();
		PaymentQueueResponse response = paymentService.confirmPaymentAsync(request, userId);

		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(response));
	}

	@Operation(
			summary = "결제 상태 조회",
			description = "대기열 결제의 현재 처리 상태를 조회합니다")
	@GetMapping("/{paymentId}/status")
	public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
			@PathVariable Long paymentId,
			@AuthenticationPrincipal CustomUserDetails userDetails) {

		PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}
}
