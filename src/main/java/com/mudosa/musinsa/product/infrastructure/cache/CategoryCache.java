package com.mudosa.musinsa.product.infrastructure.cache;

import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 카테고리 정보를 Redis에 캐싱한다.
 */
@Component
@RequiredArgsConstructor
public class CategoryCache {

	private static final String TREE_KEY = "category:tree";
	private static final String CATEGORY_KEY_PREFIX = "category:id:";

	private final RedisTemplate<String, Object> redisTemplate;

	public void saveTree(CategoryTreeResponse tree) {
		redisTemplate.opsForValue().set(TREE_KEY, tree);
	}

	public CategoryTreeResponse getTree() {
		Object cached = redisTemplate.opsForValue().get(TREE_KEY);
		return cached instanceof CategoryTreeResponse ? (CategoryTreeResponse) cached : null;
	}

	public void saveAll(Map<Long, CategoryTreeResponse.CategoryNode> categories) {
		if (categories == null || categories.isEmpty()) {
			return;
		}
		Map<String, Object> bulk = categories.entrySet().stream()
			.filter(entry -> entry.getKey() != null && entry.getValue() != null)
			.collect(Collectors.toMap(entry -> buildKey(entry.getKey()), Map.Entry::getValue));
		if (!bulk.isEmpty()) {
			redisTemplate.opsForValue().multiSet(bulk);
		}
	}

	public CategoryTreeResponse.CategoryNode get(Long categoryId) {
		if (categoryId == null) {
			return null;
		}
		Object cached = redisTemplate.opsForValue().get(buildKey(categoryId));
		return cached instanceof CategoryTreeResponse.CategoryNode ? (CategoryTreeResponse.CategoryNode) cached : null;
	}

	public Map<Long, CategoryTreeResponse.CategoryNode> getAll(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		var keyList = ids.stream()
			.filter(id -> id != null)
			.map(id -> Map.entry(id, buildKey(id)))
			.toList();

		var values = redisTemplate.opsForValue().multiGet(
			keyList.stream().map(Map.Entry::getValue).toList());

		Map<Long, CategoryTreeResponse.CategoryNode> result = new java.util.HashMap<>();
		for (int i = 0; i < keyList.size(); i++) {
			Object raw = values != null && i < values.size() ? values.get(i) : null;
			if (raw instanceof CategoryTreeResponse.CategoryNode node) {
				result.put(keyList.get(i).getKey(), node);
			}
		}
		return result;
	}

	private String buildKey(Long categoryId) {
		return CATEGORY_KEY_PREFIX + categoryId;
	}
}
