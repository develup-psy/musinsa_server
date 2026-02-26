package com.mudosa.musinsa.product.application.mapper;

import com.mudosa.musinsa.product.application.dto.ProductDetailResponse;
import com.mudosa.musinsa.product.application.dto.ProductSearchResponse;
import com.mudosa.musinsa.product.domain.model.Image;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductOption;
import com.mudosa.musinsa.product.domain.model.ProductOptionValue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상품 조회 응답 변환을 담당하는 임시 매퍼.
 */
public final class ProductQueryMapper {

	private ProductQueryMapper() {
	}

	// 상품 목록을 응답 DTO로 변환한다.
	public static ProductSearchResponse toSearchResponse(List<ProductSearchResponse.ProductSummary> summaries,
			String nextCursor, boolean hasNext, Long totalCount) {
		return ProductSearchResponse.builder()
				.products(summaries)
				.nextCursor(nextCursor)
				.hasNext(hasNext)
				.totalCount(totalCount)
				.build();
	}

	// 상품 상세 조회 응답으로 변환한다.
	public static ProductDetailResponse toProductDetail(Product product,
			List<ProductDetailResponse.ImageResponse> images,
			List<ProductDetailResponse.OptionDetail> options) {
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
				.images(images)
				.options(options)
				.build();
	}

	// 이미지 엔티티를 응답 DTO로 변환한다.
	public static ProductDetailResponse.ImageResponse toImageResponse(Image image) {
		return ProductDetailResponse.ImageResponse.builder()
				.imageId(image.getImageId())
				.imageUrl(image.getImageUrl())
				.isThumbnail(Boolean.TRUE.equals(image.getIsThumbnail()))
				.build();
	}

	// 상품 옵션 엔티티를 응답 DTO로 변환한다.
	public static ProductDetailResponse.OptionDetail toOptionDetail(ProductOption option,
			List<ProductOptionValue> optionValues,
			Map<Long, OptionValueInfo> optionValueInfoMap) {
		List<ProductDetailResponse.OptionDetail.OptionValueDetail> optionValueDetails = optionValues.stream()
				.map(mapping -> {
					Long optionValueId = mapping.getId() != null ? mapping.getId().getOptionValueId() : null;
					OptionValueInfo cached = optionValueId != null && optionValueInfoMap != null
							? optionValueInfoMap.get(optionValueId)
							: null;
					return ProductDetailResponse.OptionDetail.OptionValueDetail.builder()
							.optionValueId(optionValueId)
							.optionName(cached != null ? cached.optionName() : null)
							.optionValue(cached != null ? cached.optionValue() : null)
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

	public static record OptionValueInfo(Long optionValueId, String optionName, String optionValue) {}
}
