package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.CartItemDetailResponse;
import com.mudosa.musinsa.product.application.dto.CartItemResponse;
import com.mudosa.musinsa.security.CustomUserDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CartController 권한 테스트")
class CartControllerTest extends ControllerTestSupport {

    private Long userId;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        userId = 1L;
        userDetails = new CustomUserDetails(userId, "USER");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("unauthorizedRequests")
    @DisplayName("인증 없이 장바구니 API 접근 시 401을 반환한다.")
    void unauthorizedAccessReturns401(String description, MockHttpServletRequestBuilder requestBuilder) throws Exception {
        // when // then
        mockMvc.perform(requestBuilder)
            .andExpect(status().isUnauthorized());
    }

    private static Stream<Arguments> unauthorizedRequests() {
        return Stream.of(
            Arguments.of("GET cart 목록", get("/api/cart")),
            Arguments.of("POST cart 추가", post("/api/cart")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productOptionId\":1,\"quantity\":1}")
                .with(csrf())),
            Arguments.of("PATCH cart 수량 변경", patch("/api/cart/{cartItemId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}")
                .with(csrf())),
            Arguments.of("DELETE cart 삭제", delete("/api/cart/{cartItemId}", 1L)
                .with(csrf()))
        );
    }

    @Test
    @DisplayName("인증된 사용자는 장바구니를 조회할 수 있다.")
    void authorizedAccessReturns200() throws Exception {
        // given
        given(cartService.getCartItems(anyLong())).willReturn(
            List.of(
                CartItemDetailResponse.builder()
                    .cartItemId(10L)
                    .userId(userId)
                    .productId(20L)
                    .productOptionId(30L)
                    .productName("상품명")
                    .productInfo("상품 정보")
                    .brandName("브랜드")
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(10000))
                    .stockQuantity(5)
                    .hasStock(true)
                    .build()
            )
        );

        // when // then
        mockMvc.perform(get("/api/cart")
                .with(user(userDetails)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productOptionId").value(30L))
            .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    @DisplayName("인증된 사용자는 장바구니에 상품을 담을 수 있다.")
    void authorizedAddReturns201() throws Exception {
        // given
        given(cartService.addCartItem(eq(userId), any())).willReturn(
            CartItemResponse.builder()
                .cartItemId(10L)
                .userId(userId)
                .productOptionId(30L)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(10000))
                .build()
        );

        // when // then
        mockMvc.perform(post("/api/cart")
                .with(user(userDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productOptionId\":30,\"quantity\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.productOptionId").value(30L))
            .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    @DisplayName("인증된 사용자는 장바구니 수량을 변경할 수 있다.")
    void authorizedUpdateReturns200() throws Exception {
        // given
        given(cartService.updateCartItemQuantity(eq(userId), eq(99L), anyInt())).willReturn(
            CartItemResponse.builder()
                .cartItemId(99L)
                .userId(userId)
                .productOptionId(30L)
                .quantity(5)
                .unitPrice(BigDecimal.valueOf(10000))
                .build()
        );

        // when // then
        mockMvc.perform(patch("/api/cart/{cartItemId}", 99L)
                .with(user(userDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cartItemId").value(99L))
            .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    @DisplayName("인증된 사용자는 장바구니 항목을 삭제할 수 있다.")
    void authorizedDeleteReturns204() throws Exception {
        // when // then
        mockMvc.perform(delete("/api/cart/{cartItemId}", 77L)
                .with(user(userDetails))
                .with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 장바구니 조회 시 404를 반환한다.")
    void getCartItemsUserNotFound() throws Exception {
        Long userId = 999L;
        CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");

        // given
        given(cartService.getCartItems(userId))
            .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        // when // then
        mockMvc.perform(get("/api/cart")
                .with(user(userDetails)))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 장바구니 추가 시 404를 반환한다.")
    void addCartItemUserNotFound() throws Exception {
        Long userId = 999L;
        CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");

        // given
        given(cartService.addCartItem(eq(userId), any()))
            .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        // when // then
        mockMvc.perform(post("/api/cart")
                .with(user(userDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productOptionId\":30,\"quantity\":2}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 수량 변경 시 404를 반환한다.")
    void updateCartItemUserNotFound() throws Exception {
        Long userId = 999L;
        CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");

        // given
        given(cartService.updateCartItemQuantity(eq(userId), eq(1L), anyInt()))
            .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        // when // then
        mockMvc.perform(patch("/api/cart/{cartItemId}", 1L)
                .with(user(userDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 장바구니 항목 삭제 시 404를 반환한다.")
    void deleteCartItemUserNotFound() throws Exception {
        Long userId = 999L;
        CustomUserDetails userDetails = new CustomUserDetails(userId, "USER");

        // given
        willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
            .given(cartService).deleteCartItem(eq(userId), eq(1L));

        // when // then
        mockMvc.perform(delete("/api/cart/{cartItemId}", 1L)
                .with(user(userDetails))
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidCreateRequests")
    @DisplayName("장바구니 추가 요청 DTO 검증 실패 시 400을 반환한다.")
    void addCartItemValidation(String description, String body) throws Exception {
        // given
        // when // then
        mockMvc.perform(post("/api/cart")
                .with(user(userDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());

        verify(cartService, never()).addCartItem(anyLong(), any());
    }

    private static Stream<Arguments> invalidCreateRequests() {
        return Stream.of(
            Arguments.of("quantity null", "{\"productOptionId\":30,\"quantity\":null}"),
            Arguments.of("quantity 0", "{\"productOptionId\":30,\"quantity\":0}")
        );
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidUpdateRequests")
    @DisplayName("장바구니 수량 변경 요청 DTO 검증 실패 시 400을 반환한다.")
    void updateCartItemValidation(String description, String body) throws Exception {
        // when // then
        mockMvc.perform(patch("/api/cart/{cartItemId}", 1L)
                .with(user(userDetails))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());

        verify(cartService, never()).updateCartItemQuantity(anyLong(), anyLong(), anyInt());
    }

    private static Stream<Arguments> invalidUpdateRequests() {
        return Stream.of(
            Arguments.of("quantity null", "{\"quantity\":null}"),
            Arguments.of("quantity 0", "{\"quantity\":0}")
        );
    }
}
