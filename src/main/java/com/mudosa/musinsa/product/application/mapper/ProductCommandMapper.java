package com.mudosa.musinsa.product.application.mapper;

import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductManagerResponse;
import com.mudosa.musinsa.product.application.dto.ProductOptionStockResponse;
import com.mudosa.musinsa.product.domain.model.OptionValue;
import com.mudosa.musinsa.product.domain.model.Inventory;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.model.ProductOptionValue;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 상품 도메인 객체를 응답 DTO로 역정규화하는 매핑 유틸리티.
 * 여러 테이블의 데이터를 하나의 응답으로 조합하고 복잡한 비즈니스 로직을 포함한다.
 */
// TODO: querydsl 도입 후 제거 검토
public final class ProductCommandMapper {

    public static ProductDetailResponse toProductDetail(Product product) {
        List<ProductDetailResponse.ImageResponse> imageResponses = product.getImages().stream()
            .map(image -> ProductDetailResponse.ImageResponse.builder()
                    .imageId(image.getImageId())
                    .imageUrl(image.getImageUrl())
                    .isThumbnail(Boolean.TRUE.equals(image.getIsThumbnail()))
                    .build())
                    .collect(Collectors.toList());

        List<ProductDetailResponse.OptionDetail> optionDetails = product.getProductOptions().stream()
            .map(ProductCommandMapper::toOptionDetail)
            .collect(Collectors.toList());

        return ProductDetailResponse.builder()
                .productId(product.getProductId())
                .brandId(product.getBrand() != null ? product.getBrand().getBrandId() : null)
                .brandName(product.getBrandName())
                .productName(product.getProductName())
                .productInfo(product.getProductInfo())
                .productGenderType(product.getProductGenderType() != null
                ? product.getProductGenderType().name()
                : null)
            .isAvailable(product.getIsAvailable())
            .categoryPath(product.getCategoryPath())
            .images(imageResponses)
            .options(optionDetails)
            .build();
    }

    public static ProductDetailResponse.OptionDetail toOptionDetail(ProductOption option) {
        List<ProductDetailResponse.OptionDetail.OptionValueDetail> optionValueDetails = option.getProductOptionValues().stream()
            .map(ProductCommandMapper::toOptionValueDetail)
            .collect(Collectors.toList());

        Integer stockQuantity = null;
        Boolean hasStock = null;
        if (option.getInventory() != null && option.getInventory().getStockQuantity() != null) {
            stockQuantity = option.getInventory().getStockQuantity().getValue();
            hasStock = stockQuantity > 0;
        }

        return ProductDetailResponse.OptionDetail.builder()
                .optionId(option.getProductOptionId())
                .productPrice(option.getProductPrice() != null ? option.getProductPrice().getAmount() : null)
                .stockQuantity(stockQuantity)
                .hasStock(hasStock)
                .optionValues(optionValueDetails)
                .build();
    }

    public static ProductOptionStockResponse toOptionStockResponse(ProductOption productOption) {
        return toOptionStockResponse(productOption, productOption.getInventory());
    }

    public static ProductOptionStockResponse toOptionStockResponse(ProductOption productOption,
                                                                   Inventory inventory) {
        Inventory effectiveInventory = inventory != null ? inventory : productOption.getInventory();
        Integer stockQuantity = null;
        boolean hasStock = false;

        if (effectiveInventory != null && effectiveInventory.getStockQuantity() != null) {
            stockQuantity = effectiveInventory.getStockQuantity().getValue();
            hasStock = stockQuantity > 0;
        }

        java.math.BigDecimal productPrice = productOption.getProductPrice() != null
            ? productOption.getProductPrice().getAmount()
            : null;

        List<ProductOptionStockResponse.OptionValueSummary> optionValueSummaries = productOption.getProductOptionValues().stream()
            .map(ProductCommandMapper::toOptionValueSummary)
            .collect(Collectors.toList());

        Product product = productOption.getProduct();
        String productName = product != null ? product.getProductName() : null;

        return ProductOptionStockResponse.builder()
            .productOptionId(productOption.getProductOptionId())
            .productName(productName)
            .productPrice(productPrice)
            .stockQuantity(stockQuantity)
            .hasStock(hasStock)
            .optionValues(optionValueSummaries)
            .build();
    }

    private static ProductOptionStockResponse.OptionValueSummary toOptionValueSummary(ProductOptionValue productOptionValue) {
        OptionValue optionValue = productOptionValue.getOptionValue();
        return ProductOptionStockResponse.OptionValueSummary.builder()
            .optionValueId(optionValue != null ? optionValue.getOptionValueId() : null)
            .optionName(optionValue != null ? optionValue.getOptionName() : null)
            .optionValue(optionValue != null ? optionValue.getOptionValue() : null)
            .build();
    }

    private static ProductDetailResponse.OptionDetail.OptionValueDetail toOptionValueDetail(ProductOptionValue mapping) {
        OptionValue optionValue = mapping.getOptionValue();
        String optionName = optionValue != null ? optionValue.getOptionName() : null;
        if (optionName != null) {
            optionName = optionName.trim();
        }
        return ProductDetailResponse.OptionDetail.OptionValueDetail.builder()
            .optionValueId(optionValue != null ? optionValue.getOptionValueId() : null)
            .optionName(optionName)
            .optionValue(optionValue != null ? optionValue.getOptionValue() : null)
            .build();
    }

    public static ProductManagerResponse toManagerResponse(Product product) {
        List<ProductManagerResponse.ImageInfo> imageInfos = product.getImages().stream()
            .map(image -> ProductManagerResponse.ImageInfo.builder()
                    .imageId(image.getImageId())
                    .imageUrl(image.getImageUrl())
                    .isThumbnail(image.getIsThumbnail())
                    .build())
                    .collect(Collectors.toList());

        List<ProductManagerResponse.OptionInfo> optionInfos = product.getProductOptions().stream()
            .map(option -> ProductManagerResponse.OptionInfo.builder()
                    .optionId(option.getProductOptionId())
                    .price(option.getProductPrice() != null ? option.getProductPrice().getAmount() : null)
                    .stockQuantity(option.getInventory().getStockQuantity().getValue())
                    .optionValues(option.getProductOptionValues().stream()
                    .map(pov -> pov.getOptionValue().getOptionValue())
                    .collect(Collectors.toList()))
                    .build())
                    .collect(Collectors.toList());

        return ProductManagerResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productInfo(product.getProductInfo())
                .isAvailable(product.getIsAvailable())
                .brandName(product.getBrandName())
                .categoryPath(product.getCategoryPath())
                .productGenderType(product.getProductGenderType())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .images(imageInfos)
                .options(optionInfos)
                .build();
    }
}
