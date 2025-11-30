package com.mudosa.musinsa.product.infrastructure.cache;

import com.mudosa.musinsa.product.application.ProductQueryService;
import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import com.mudosa.musinsa.product.domain.model.OptionValue;
import com.mudosa.musinsa.product.domain.repository.OptionValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 애플리케이션 기동 시 카테고리/옵션 값을 Redis에 적재한다.
 */
@Component
@ConditionalOnProperty(name = "cache.preload.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CacheInitializer {

	private final ProductQueryService productQueryService;
	private final OptionValueRepository optionValueRepository;
	private final CategoryCache categoryCache;
	private final OptionValueCache optionValueCache;

	@EventListener(ApplicationReadyEvent.class)
	public void preloadCaches() {
		preloadOptionValues();
		preloadCategories();
	}

	private void preloadOptionValues() {
		List<OptionValue> allOptionValues = optionValueRepository.findAll();
		optionValueCache.saveAll(allOptionValues);
		log.info("Preloaded option values into Redis cache. count={}", allOptionValues.size());
	}

	private void preloadCategories() {
		CategoryTreeResponse tree = productQueryService.getCategoryTree();
		categoryCache.saveTree(tree);

		Map<Long, CategoryTreeResponse.CategoryNode> flatMap = CategoryTreeResponse.flatten(tree);
		categoryCache.saveAll(flatMap);

		log.info("Preloaded categories into Redis cache. totalNodes={}", flatMap.size());
	}


}
