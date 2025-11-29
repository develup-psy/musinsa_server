package com.mudosa.musinsa.product.presentation.controller;

import com.mudosa.musinsa.product.application.ProductCommandService;
import com.mudosa.musinsa.product.application.ProductInventoryService;
import com.mudosa.musinsa.product.application.dto.ProductCreateRequest;
import com.mudosa.musinsa.product.application.dto.ProductCreateResponse;
import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductManagerResponse;
import com.mudosa.musinsa.product.application.dto.ProductOptionCreateRequest;
import com.mudosa.musinsa.product.application.dto.ProductOptionStockResponse;
import com.mudosa.musinsa.product.application.dto.ProductUpdateRequest;
import com.mudosa.musinsa.product.application.dto.StockAdjustmentRequest;
import com.mudosa.musinsa.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

// 브랜드 관리자가 상품을 생성, 수정, 삭제하고 옵션을 관리하는 엔드포인트를 제공한다.
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
@RequestMapping("/api/brands/{brandId}/products")
public class ProductCommandController {

    private final ProductCommandService productService;
    private final ProductInventoryService productInventoryService;

    // 상품 생성
    @PostMapping
    public ResponseEntity<ProductCreateResponse> createProduct(
            @PathVariable Long brandId,
            @Valid @RequestBody ProductCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        Long productId = productService.createProduct(request, brandId, userId);
        URI location = URI.create(String.format("/api/brands/%d/products/%d", brandId, productId));
        
        return ResponseEntity.created(location)
                .body(ProductCreateResponse.builder().productId(productId).build());
    } 

    // 상품 정보 수정
    @PutMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable Long brandId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        ProductDetailResponse response = productService.updateProduct(brandId, productId, request, userId);

        return ResponseEntity.ok(response);
    }

    // 브랜드별 상품 상세 조회 (비활성 상품 포함)
    @GetMapping("/{productId}")
    public ResponseEntity<ProductManagerResponse> getProductDetail(
            @PathVariable Long brandId,
            @PathVariable Long productId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        ProductManagerResponse product = productService.getProductDetailForManager(brandId, productId, userId);

        return ResponseEntity.ok(product);
    }

    // 브랜드별 상품 목록 조회 (비활성 상품 포함)
    @GetMapping
    public ResponseEntity<List<ProductManagerResponse>> getBrandProducts(
            @PathVariable Long brandId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        List<ProductManagerResponse> products = productService.getBrandProductsForManager(brandId, userId);

        return ResponseEntity.ok(products);
    }

    // 상품 옵션 추가
    @PostMapping("/{productId}/options")
    public ResponseEntity<ProductDetailResponse.OptionDetail> addProductOption(
            @PathVariable Long brandId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductOptionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        ProductDetailResponse.OptionDetail response = productService.addProductOption(brandId, productId, request, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 상품 옵션 재고 추가 (입고)
    @PostMapping("/{productId}/inventory/increase")
    public ResponseEntity<ProductOptionStockResponse> increaseStock(
            @PathVariable Long brandId,
            @PathVariable Long productId,
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        ProductOptionStockResponse response = productInventoryService.addStock(brandId, productId, request, userId);

        return ResponseEntity.ok(response);
    }

    // 상품 옵션 재고 차감 (출고)
    @PostMapping("/{productId}/inventory/decrease")
    public ResponseEntity<ProductOptionStockResponse> decreaseStock(
        @PathVariable Long brandId,
            @PathVariable Long productId,
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        ProductOptionStockResponse response = productInventoryService.subtractStock(brandId, productId, request, userId);

        return ResponseEntity.ok(response);
    }

    // 브랜드별 상품 옵션 재고 목록 조회
    @GetMapping("/{productId}/inventory")
    public ResponseEntity<List<ProductOptionStockResponse>> getProductOptionStocks(
            @PathVariable Long brandId,
            @PathVariable Long productId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();
        List<ProductOptionStockResponse> response = productInventoryService.getProductOptionStocks(brandId, productId, userId);

        return ResponseEntity.ok(response);
    }

}
