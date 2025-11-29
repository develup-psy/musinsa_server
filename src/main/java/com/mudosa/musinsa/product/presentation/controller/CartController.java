package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.product.application.CartService;
import com.mudosa.musinsa.product.application.dto.CartItemCreateRequest;
import com.mudosa.musinsa.product.application.dto.CartItemDetailResponse;
import com.mudosa.musinsa.product.application.dto.CartItemResponse;
import com.mudosa.musinsa.product.application.dto.CartItemUpdateRequest;
import com.mudosa.musinsa.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 사용자 장바구니 CRUD를 노출하는 컨트롤러이다.
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    // 사용자 장바구니 상품 조회
    @GetMapping
    public ResponseEntity<List<CartItemDetailResponse>> getCartItems(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUserId();
        List<CartItemDetailResponse> response = cartService.getCartItems(userId);
        return ResponseEntity.ok(response);
    }

    // 사용자 장바구니 상품 추가
    @PostMapping
    public ResponseEntity<CartItemResponse> addCartItem(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                        @Valid @RequestBody CartItemCreateRequest request) {
        Long userId = userDetails.getUserId();
        CartItemResponse response = cartService.addCartItem(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 사용자 장바구니 상품 수량 수정
    @PatchMapping("/{cartItemId}")
    public ResponseEntity<CartItemResponse> updateCartItem(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                           @PathVariable Long cartItemId,
                                                           @Valid @RequestBody CartItemUpdateRequest request) {
        Long userId = userDetails.getUserId();
        CartItemResponse response = cartService.updateCartItemQuantity(userId, cartItemId, request.getQuantity());
        return ResponseEntity.ok(response);
    }

    // 사용자 장바구니 상품 삭제
    @DeleteMapping("/{cartItemId}")
    public ResponseEntity<Void> deleteCartItem(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @PathVariable Long cartItemId) {
        Long userId = userDetails.getUserId();
        cartService.deleteCartItem(userId, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
