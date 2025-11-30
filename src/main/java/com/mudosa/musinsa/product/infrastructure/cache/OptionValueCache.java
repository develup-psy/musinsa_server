package com.mudosa.musinsa.product.infrastructure.cache;

import com.mudosa.musinsa.product.domain.model.OptionValue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 옵션 값 ID에 대한 이름/값을 Redis에 캐싱한다.
 */
@Component
@RequiredArgsConstructor
public class OptionValueCache {

	private static final String KEY_PREFIX = "optionValue:";

	private final RedisTemplate<String, Object> redisTemplate;

	public void saveAll(Collection<OptionValue> optionValues) {
		if (optionValues == null || optionValues.isEmpty()) {
			return;
		}
		Map<String, Value> bulk = optionValues.stream()
			.filter(ov -> ov != null && ov.getOptionValueId() != null)
			.collect(Collectors.toMap(ov -> buildKey(ov.getOptionValueId()),
				ov -> new Value(ov.getOptionName(), ov.getOptionValue())));
		if (!bulk.isEmpty()) {
			redisTemplate.opsForValue().multiSet(bulk);
		}
	}

	public void save(OptionValue optionValue) {
		if (optionValue == null || optionValue.getOptionValueId() == null) {
			return;
		}
		Value value = new Value(optionValue.getOptionName(), optionValue.getOptionValue());
		redisTemplate.opsForValue().set(buildKey(optionValue.getOptionValueId()), value);
	}

	public Value get(Long optionValueId) {
		if (optionValueId == null) {
			return null;
		}
		Object cached = redisTemplate.opsForValue().get(buildKey(optionValueId));
		return cached instanceof Value ? (Value) cached : null;
	}

	public Map<Long, Value> getAll(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		var keyList = ids.stream()
			.filter(id -> id != null)
			.map(id -> Map.entry(id, buildKey(id)))
			.toList();

		var values = redisTemplate.opsForValue().multiGet(
			keyList.stream().map(Map.Entry::getValue).toList());

		Map<Long, Value> result = new java.util.HashMap<>();
		for (int i = 0; i < keyList.size(); i++) {
			Object raw = values != null && i < values.size() ? values.get(i) : null;
			if (raw instanceof Value value) {
				result.put(keyList.get(i).getKey(), value);
			}
		}
		return result;
	}

	private String buildKey(Long id) {
		return KEY_PREFIX + id;
	}

	/**
	 * 캐시에 저장할 값 객체.
	 */
	public record Value(String optionName, String optionValue) {}
}
