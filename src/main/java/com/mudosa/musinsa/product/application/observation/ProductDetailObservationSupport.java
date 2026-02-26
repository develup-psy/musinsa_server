package com.mudosa.musinsa.product.application.observation;

import com.mudosa.musinsa.exception.BusinessException;
import com.mudosa.musinsa.exception.ErrorCode;
import com.mudosa.musinsa.product.domain.model.Image;
import com.mudosa.musinsa.product.domain.model.Product;
import com.mudosa.musinsa.product.domain.model.ProductOptionValue;
import com.mudosa.musinsa.product.domain.repository.ProductRepository;
import com.mudosa.musinsa.product.application.mapper.ProductQueryMapper;
import com.mudosa.musinsa.product.infrastructure.cache.OptionValueCache;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductDetailObservationSupport {

    private final ProductRepository productRepository;
    private final OptionValueCache optionValueCache;

    @Observed(name = "product.detail.fetch-product", contextualName = "product.detail.fetch-product")
    public Product fetchProductWithOptions(Long productId) {
        return productRepository.findDetailById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,"해당 상품을 찾을 수 없거나 비활성화된 상품입니다"));
    }

    @Observed(name = "product.detail.fetch-images", contextualName = "product.detail.fetch-images")
    public List<Image> fetchImages(Long productId) {
        return productRepository.findImagesByProductId(productId);
    }

    @Observed(name = "product.detail.fetch-option-values", contextualName = "product.detail.fetch-option-values")
    public List<ProductOptionValue> fetchProductOptionValues(Long productId) {
        return productRepository.findProductOptionValuesByProductId(productId);
    }

    @Observed(name = "product.detail.group-option-values", contextualName = "product.detail.group-option-values")
    public Map<Long, List<ProductOptionValue>> groupOptionValuesByOptionId(List<ProductOptionValue> optionValues) {
        return optionValues.stream()
            .filter(pov -> pov.getId() != null && pov.getId().getProductOptionId() != null)
            .collect(Collectors.groupingBy(pov -> pov.getId() != null ? pov.getId().getProductOptionId() : null));
    }

    @Observed(name = "product.detail.fetch-optionvalue-cache", contextualName = "product.detail.fetch-optionvalue-cache")
    public Map<Long, ProductQueryMapper.OptionValueInfo> buildOptionValueInfoMap(List<ProductOptionValue> optionValues) {
        Set<Long> optionValueIds = optionValues.stream()
            .map(mapping -> mapping.getId() != null ? mapping.getId().getOptionValueId() : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, OptionValueCache.Value> cachedOptionValues = optionValueCache.getAll(optionValueIds);
        return cachedOptionValues.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new ProductQueryMapper.OptionValueInfo(
                    entry.getKey(),
                    entry.getValue().optionName(),
                    entry.getValue().optionValue()
                )));
    }
}
