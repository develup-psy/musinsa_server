//package com.mudosa.musinsa.product.infrastructure.cache;
//
//import com.mudosa.musinsa.product.domain.model.OptionValue;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoSettings;
//import org.mockito.quality.Strictness;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.ValueOperations;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyMap;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.doAnswer;
//import static org.mockito.Mockito.lenient;
//import static org.mockito.Mockito.when;
//
//import java.util.stream.StreamSupport;
//
//@ExtendWith(MockitoExtension.class)
//@MockitoSettings(strictness = Strictness.LENIENT)
//class OptionValueCacheTest {
//
//    @Mock
//    private RedisTemplate<String, Object> redisTemplate;
//
//    @Mock
//    private ValueOperations<String, Object> valueOperations;
//
//    @InjectMocks
//    private OptionValueCache optionValueCache;
//
//    private final Map<String, Object> store = new HashMap<>();
//
//    @BeforeEach
//    void setUp() {
//        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
//		doAnswer(invocation -> {
//			String key = invocation.getArgument(0);
//			Object value = invocation.getArgument(1);
//			store.put(key, value);
//			return null;
//		}).when(valueOperations).set(anyString(), any());
//		doAnswer(invocation -> {
//			Map<String, Object> map = invocation.getArgument(0);
//			store.putAll(map);
//			return null;
//		}).when(valueOperations).multiSet(anyMap());
//        lenient().when(valueOperations.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
//        when(valueOperations.multiGet(any())).thenAnswer(invocation -> {
//            Iterable<String> keys = invocation.getArgument(0);
//            return StreamSupport.stream(keys.spliterator(), false)
//                .map(store::get)
//                .toList();
//        });
//        store.clear();
//    }
//
//    @Test
//    void saveAndGet_singleOptionValue() {
//        OptionValue optionValue = OptionValue.create("color", "red");
//        ReflectionTestUtils.setField(optionValue, "optionValueId", 1L);
//
//        optionValueCache.save(optionValue);
//
//        OptionValueCache.Value cached = optionValueCache.get(1L);
//        assertThat(cached).isNotNull();
//        assertThat(cached.optionName()).isEqualTo("color");
//        assertThat(cached.optionValue()).isEqualTo("red");
//    }
//
//    @Test
//    void saveAll_andGetAll_multipleOptionValues() {
//        OptionValue first = OptionValue.create("size", "M");
//        OptionValue second = OptionValue.create("material", "cotton");
//        ReflectionTestUtils.setField(first, "optionValueId", 1L);
//        ReflectionTestUtils.setField(second, "optionValueId", 2L);
//
//        optionValueCache.saveAll(List.of(first, second));
//
//        Map<Long, OptionValueCache.Value> result = optionValueCache.getAll(List.of(1L, 2L, 3L));
//        assertThat(result).hasSize(2);
//        assertThat(result.get(1L).optionName()).isEqualTo("size");
//        assertThat(result.get(2L).optionValue()).isEqualTo("cotton");
//        assertThat(result).doesNotContainKey(3L);
//    }
//}
