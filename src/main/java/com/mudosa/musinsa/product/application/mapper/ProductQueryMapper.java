package com.mudosa.musinsa.product.application.mapper;

import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.product.domain.model.OptionValue;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductOption;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 상품 조회 응답 변환을 담당하는 임시 매퍼.
 */
public final class ProductQueryMapper {

	private ProductQueryMapper() {
	}

	// 상품 목록 페이지를 응답 DTO로 변환한다.
	public static ProductSearchResponse toSearchResponse(Page<Product> page, Pageable pageable) {
		List<ProductSearchResponse.ProductSummary> summaries = page.getContent().stream()
			.map(ProductQueryMapper::toProductSummary)
			.collect(Collectors.toList());

		int pageNumber = pageable.isPaged() ? pageable.getPageNumber() : 0;
		int pageSize = pageable.isPaged() ? pageable.getPageSize() : summaries.size();

		return ProductSearchResponse.builder()
		        .products(summaries)
		        .totalElements(page.getTotalElements())
		        .totalPages(page.getTotalPages())
		        .page(pageNumber)
		        .size(pageSize)
		        .build();
	}

    // 상품 목록 조회 응답의 요약 정보를 변환한다.
    public static ProductSearchResponse.ProductSummary toProductSummary(Product product) {
        BigDecimal lowestPrice = product.getDefaultPrice() != null
            ? product.getDefaultPrice()
            : BigDecimal.ZERO;

        String thumbnailUrl = product.getImages().stream()
            .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
            .map(image -> image.getImageUrl())
            .findFirst()
			.orElse(null);

		return ProductSearchResponse.ProductSummary.builder()
		        .productId(product.getProductId())
		        .brandId(product.getBrand() != null ? product.getBrand().getBrandId() : null)
		        .brandName(product.getBrandName())
		        .productName(product.getProductName())
		        .productInfo(product.getProductInfo())
		        .productGenderType(product.getProductGenderType() != null
				? product.getProductGenderType().name()
                : null)
            .isAvailable(product.getIsAvailable())
            .hasStock(null) // 목록에서 옵션 재고를 계산하지 않는다.
            .lowestPrice(lowestPrice)
            .thumbnailUrl(thumbnailUrl)
            .categoryPath(product.getCategoryPath())
            .build();
    }

    // 상품 상세 조회 응답으로 변환한다.
	public static ProductDetailResponse toProductDetail(Product product) {
		List<ProductDetailResponse.ImageResponse> imageResponses = product.getImages().stream()
			.map(image -> ProductDetailResponse.ImageResponse.builder()
			        .imageId(image.getImageId())
			        .imageUrl(image.getImageUrl())
			        .isThumbnail(Boolean.TRUE.equals(image.getIsThumbnail()))
			        .build())
			        .collect(Collectors.toList());

		List<ProductDetailResponse.OptionDetail> optionDetails = product.getProductOptions().stream()
			.map(ProductQueryMapper::toOptionDetail)
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
			.map(mapping -> {
				OptionValue optionValue = mapping.getOptionValue();
				return ProductDetailResponse.OptionDetail.OptionValueDetail.builder()
				        .optionValueId(optionValue != null ? optionValue.getOptionValueId() : null)
				        .optionName(optionValue != null ? optionValue.getOptionName() : null)
				        .optionValue(optionValue != null ? optionValue.getOptionValue() : null)
				        .build();
			})
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
}
