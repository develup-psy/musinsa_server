package com.mudosa.musinsa.product.application;

import com.mudosa.musinsa.brand.domain.repository.BrandMemberRepository;
import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.application.dto.ProductOptionStockResponse;
import com.mudosa.musinsa.product.application.dto.StockAdjustmentRequest;
import com.mudosa.musinsa.product.application.mapper.ProductCommandMapper;
import com.mudosa.musinsa.product.domain.model.Inventory;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.repository.InventoryRepository;
import com.mudosa.musinsa.product.domain.repository.ProductOptionRepository;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 관리자용 상품 재고 관리 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductInventoryService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final InventoryRepository inventoryRepository;
    private final BrandMemberRepository brandMemberRepository;

    /**
     * 브랜드 관리자가 특정 상품의 모든 옵션 재고 현황을 조회한다.
     */
    public List<ProductOptionStockResponse> getProductOptionStocks(Long brandId,
                                                                   Long productId,
                                                                   Long userId) {
        // 1. 브랜드 멤버십 및 상품 소유 검증                                                           
        Product product = validateBrandAuthorization(brandId, productId, userId);

        // 2. 상품 옵션 재고 현황 매핑 후 반환
        return product.getProductOptions().stream()
            .map(ProductCommandMapper::toOptionStockResponse)
            .collect(Collectors.toList());
    }

    /**
     * 브랜드 관리자가 특정 상품 옵션의 재고를 증가시킨다.
     */
    @Transactional
    public ProductOptionStockResponse addStock(Long brandId,
                                               Long productId,
                                               StockAdjustmentRequest request,
                                               Long userId) {
        // 1. 브랜드 멤버십 및 상품 소유 검증                                        
        Product product = validateBrandAuthorization(brandId, productId, userId);

        // 2. 상품 옵션 검증 및 로드
        ProductOption productOption = loadProductOptionForProduct(product, productId, request.getProductOptionId());

        // 3. 재고 증가 처리
        Inventory updatedInventory = adjustStock(productOption.getProductOptionId(), request.getQuantity(), true);

        // 4. 결과 매핑 후 반환
        return ProductCommandMapper.toOptionStockResponse(productOption, updatedInventory);
    }

    /**
     * 브랜드 관리자가 특정 상품 옵션의 재고를 감소시킨다.
     */
    @Transactional
    public ProductOptionStockResponse subtractStock(Long brandId,
                                                    Long productId,
                                                    StockAdjustmentRequest request,
                                                    Long userId) {
        // 1. 브랜드 멤버십 및 상품 소유 검증                                               
        Product product = validateBrandAuthorization(brandId, productId, userId);

        // 2. 상품 옵션 검증 및 로드
        ProductOption productOption = loadProductOptionForProduct(product, productId, request.getProductOptionId());

        // 3. 재고 감소 처리
        Inventory updatedInventory = adjustStock(productOption.getProductOptionId(), request.getQuantity(), false);

        // 4. 결과 매핑 후 반환
        return ProductCommandMapper.toOptionStockResponse(productOption, updatedInventory);
    }

    /**
     * 재고를 조정한다. (증가 또는 감소)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Inventory adjustStock(Long productOptionId, Integer quantity, boolean isIncrease) {
        // 로그 출력
        String operation = isIncrease ? "추가" : "차감";
        log.info("재고 {} 시작 - productOptionId: {}, quantity: {}", operation, productOptionId, quantity);

        // 유효성 검사
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INVENTORY_UPDATE_VALUE);
        }

        // 재고 로드 및 락 걸기
        Inventory inventory = loadInventoryWithLock(productOptionId);
        
        // 재고 조정
        if (isIncrease) {
            inventory.increase(quantity);
        } else {
            try {
                inventory.decrease(quantity);
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.INVENTORY_INSUFFICIENT_STOCK
                    || ex.getErrorCode() == ErrorCode.STOCK_QUANTITY_OUT_OF_STOCK) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, ex.getMessage());
                }
                throw ex;
            } catch (IllegalStateException ex) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, ex.getMessage());
            }
        }

        // 변경된 재고 저장
        inventoryRepository.save(inventory);

        // 완료 로그 출력
        log.info("재고 {} 완료 - productOptionId: {}, {} 수량: {}, 현재 재고: {}", 
            operation, productOptionId, operation, quantity, inventory.getStockQuantity());
        
        // 결과 반환
        return inventory;
    }

    // 재고를 락을 걸고 로드한다.
    private Inventory loadInventoryWithLock(Long productOptionId) {
        return inventoryRepository.findByProductOptionIdWithLock(productOptionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    // 브랜드와 상품을 검증한 후 옵션을 로드한다.
    private ProductOption loadProductOptionForProduct(Product product,
                                                     Long productId,
                                                     Long productOptionId) {
        ProductOption productOption = productOptionRepository.findByIdWithProductAndInventory(productOptionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND));

        Product optionProduct = productOption.getProduct();
        if (optionProduct == null) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        if (!Objects.equals(optionProduct.getProductId(), productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        return productOption;
    }

    // 브랜드 멤버십 및 상품 소유를 검증하고 상품을 반환한다.
    private Product validateBrandAuthorization(Long brandId,
                                               Long productId,
                                               Long userId) {
        if (!brandMemberRepository.existsByBrand_BrandIdAndUserId(brandId, userId)) {
            throw new BusinessException(ErrorCode.NOT_BRAND_MEMBER);
        }

        Product product = productRepository.findDetailByIdForManager(productId, brandId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getBrand() == null || !Objects.equals(product.getBrand().getBrandId(), brandId)) {
            throw new BusinessException(ErrorCode.NOT_BRAND_PRODUCT);
        }
        return product;
    }
}
