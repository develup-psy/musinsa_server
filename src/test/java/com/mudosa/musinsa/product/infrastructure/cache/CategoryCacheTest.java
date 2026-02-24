package com.mudosa.musinsa.product.infrastructure.cache;

import com.mudosa.musinsa.product.application.dto.CategoryTreeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryCacheTest {

	@Mock
	private RedisTemplate<String, Object> redisTemplate;

	@Mock
	private ValueOperations<String, Object> valueOperations;

	@InjectMocks
	private CategoryCache categoryCache;

	private final Map<String, Object> store = new HashMap<>();

	@BeforeEach
	void setUp() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		doAnswer(invocation -> {
			String key = invocation.getArgument(0);
			Object value = invocation.getArgument(1);
			store.put(key, value);
			return null;
		}).when(valueOperations).set(anyString(), any());
		doAnswer(invocation -> {
			Map<String, Object> map = invocation.getArgument(0);
			store.putAll(map);
			return null;
		}).when(valueOperations).multiSet(anyMap());
		lenient().when(valueOperations.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
		lenient().when(valueOperations.multiGet(any())).thenAnswer(invocation -> {
			Iterable<String> keys = invocation.getArgument(0);
			return StreamSupport.stream(keys.spliterator(), false)
				.map(store::get)
				.toList();
		});
		store.clear();
	}

	@Test
	void saveAndGetTree() {
		CategoryTreeResponse tree = CategoryTreeResponse.builder().categories(List.of()).build();
		categoryCache.saveTree(tree);

		assertThat(categoryCache.getTree()).isNotNull();
	}

	@Test
	void saveAll_andGetAll() {
		CategoryTreeResponse.CategoryNode node1 = CategoryTreeResponse.CategoryNode.builder()
			.categoryId(1L)
			.categoryName("A")
			.categoryPath("A")
			.imageUrl(null)
			.children(List.of())
			.build();
		CategoryTreeResponse.CategoryNode node2 = CategoryTreeResponse.CategoryNode.builder()
			.categoryId(2L)
			.categoryName("B")
			.categoryPath("B")
			.imageUrl(null)
			.children(List.of())
			.build();
		Map<Long, CategoryTreeResponse.CategoryNode> map = Map.of(
			1L, node1,
			2L, node2
		);

		categoryCache.saveAll(map);
		Map<Long, CategoryTreeResponse.CategoryNode> cached = categoryCache.getAll(List.of(1L, 2L, 3L));

		assertThat(cached).hasSize(2);
		assertThat(cached.get(1L).getCategoryName()).isEqualTo("A");
		assertThat(cached).doesNotContainKey(3L);
	}

	@Test
	void get_singleNode() {
		CategoryTreeResponse.CategoryNode node1 = CategoryTreeResponse.CategoryNode.builder()
			.categoryId(1L)
			.categoryName("A")
			.categoryPath("A")
			.imageUrl(null)
			.children(List.of())
			.build();
		Map<Long, CategoryTreeResponse.CategoryNode> map = Map.of(1L, node1);
		categoryCache.saveAll(map);

		CategoryTreeResponse.CategoryNode cachedNode = categoryCache.get(1L);
		CategoryTreeResponse.CategoryNode nonExistentNode = categoryCache.get(99L);

		assertThat(cachedNode).isNotNull();
		assertThat(cachedNode.getCategoryName()).isEqualTo("A");
		assertThat(nonExistentNode).isNull();
	}
}
