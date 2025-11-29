package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.CartItemCreateRequest;
import com.mudosa.musinsa.product.application.dto.CartItemDetailResponse;
import com.mudosa.musinsa.product.application.dto.CartItemResponse;
import com.mudosa.musinsa.product.domain.model.CartItem;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.repository.CartItemRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.user.domain.model.User;
import com.mudosa.musinsa.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 사용자용 장바구니 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductOptionRepository productOptionRepository;
    private final UserRepository userRepository;

    /**
     * 장바구니 상품 추가
     */
    @Transactional
    public CartItemResponse addCartItem(Long userId, CartItemCreateRequest request) {
        // 1. 사용자 조회
        User user = getUserOrThrow(userId);

        // 2. 상품 옵션 조회 및 유효성 검사
        ProductOption productOption = getProductOptionWithInventoryOrThrow(request.getProductOptionId());

        // 3. 옵션 사용 가능 여부 검증
        validateOptionAvailability(productOption);

        // 4. 기존 장바구니 항목 확인 및 수량 업데이트 또는 새 항목 생성
        CartItem cartItem = cartItemRepository.findByUserIdAndProductOptionId(userId, productOption.getProductOptionId())
            .map(existing -> updateQuantity(existing, productOption, request.getQuantity()))
            .orElseGet(() -> createNewCartItem(user, productOption, request.getQuantity()));

        // 5. 장바구니 항목 저장 및 응답 반환
        CartItem saved = cartItemRepository.save(cartItem);
        return CartItemResponse.from(saved);
    }

    /**
     * 장바구니 상품 목록 조회
     */
    public List<CartItemDetailResponse> getCartItems(Long userId) {
        // 1. 사용자 존재 여부 확인
        getUserOrThrow(userId);

        // 2. 장바구니 항목 조회 및 매핑
        return cartItemRepository.findAllWithDetailsByUserId(userId).stream()
            .map(this::mapToDetailResponse)
            .collect(Collectors.toList());
    }

    /**
     * 장바구니 상품 수량 수정
     */
    @Transactional
    public CartItemResponse updateCartItemQuantity(Long userId,
                                                   Long cartItemId,
                                                   Integer quantity) {
        // 1. 수량 양수 검증                                            
        requirePositiveQuantity(quantity);

        // 2. 장바구니 항목 조회
        CartItem cartItem = getCartItemOrThrow(cartItemId);

        // 3. 소유자 검증
        validateOwnership(cartItem, userId);

        // 4. 상품 옵션 조회
        ProductOption productOption = getProductOptionWithInventoryOrThrow(
            cartItem.getProductOption() != null ? cartItem.getProductOption().getProductOptionId() : null
        );

        // 5. 옵션 사용 가능 여부 및 요청 수량 검증
        validateOptionAvailability(productOption);
        verifyRequestedQuantity(productOption, quantity);

        // 6. 수량 변경 및 저장
        cartItem.changeQuantity(quantity);

        // 7. 응답 반환
        CartItem saved = cartItemRepository.save(cartItem);
        return CartItemResponse.from(saved);
    }

    /** 
     * 장바구니 삭제 - 상품 옵션 리스트로 
     */
    @Transactional
    public void deleteCartItemsByProductOptions(Long userId, List<Long> productOptionIds) {
        if (productOptionIds == null || productOptionIds.isEmpty()) {
            return;
        }
        cartItemRepository.deleteByUserIdAndProductOptionIdIn(
                userId,
                productOptionIds
        );
    }

    /** 
     * 장바구니 삭제 - 개별 항목
     */
    @Transactional
    public void deleteCartItem(Long userId, Long cartItemId) {
        // 1. 장바구니 항목 조회
        CartItem cartItem = getCartItemOrThrow(cartItemId);
        
        // 2. 소유자 검증
        validateOwnership(cartItem, userId);

        // 3. 장바구니 항목 삭제
        cartItemRepository.delete(cartItem);
    }

    // 카트 아이템 업데이트
    private CartItem updateQuantity(CartItem cartItem, ProductOption productOption, Integer additionalQuantity) {
        requirePositiveQuantity(additionalQuantity);
        int targetQuantity = cartItem.getQuantity() + additionalQuantity;
        verifyRequestedQuantity(productOption, targetQuantity);
        cartItem.changeQuantity(targetQuantity);
        return cartItem;
    }

    // 새 카트 아이템 생성
    private CartItem createNewCartItem(User user, ProductOption productOption, Integer quantity) {
        verifyRequestedQuantity(productOption, quantity);
        return CartItem.builder()
                .user(user)
                .productOption(productOption)
                .quantity(quantity)
                .build();
    }

    // 상품 옵션 유효성 검사
    private void validateOptionAvailability(ProductOption productOption) {
        if (productOption.getProduct() == null || Boolean.FALSE.equals(productOption.getProduct().getIsAvailable())) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_AVAILABLE);
        }
        productOption.validateAvailable();
    }

    // 요청 수량 검증
    private void verifyRequestedQuantity(ProductOption productOption, Integer quantity) {
        requirePositiveQuantity(quantity);

        Integer stock = productOption.getInventory() != null && productOption.getInventory().getStockQuantity() != null
            ? productOption.getInventory().getStockQuantity().getValue()
            : null;

        if (stock < quantity) {
            throw new BusinessException(ErrorCode.CART_ITEM_INSUFFICIENT_STOCK);
        }
    }

    // CartItem을 CartItemDetailResponse로 매핑
    private CartItemDetailResponse mapToDetailResponse(CartItem cartItem) {
        return CartItemDetailResponse.from(cartItem);
    }

    // 상품 옵션 조회
    private ProductOption getProductOptionWithInventoryOrThrow(Long productOptionId) {
        if (productOptionId == null) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }
        return productOptionRepository.findByIdWithProductAndInventory(productOptionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND));
    }

    // 장바구니 항목 조회
    private CartItem getCartItemOrThrow(Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    // 사용자 조회
    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // 소유자 검증
    private void validateOwnership(CartItem cartItem, Long userId) {
        if (cartItem.getUser() == null || !Objects.equals(cartItem.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_ONLY_ACCESS_PERSONAL);
        }
    }

    // 수량 양수 검증
    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null) {
            throw new BusinessException(ErrorCode.CART_ITEM_STOCK_QUANTITY_REQUIRED);
        }
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CART_ITEM_UPDATE_VALUE);
        }
    }
}
