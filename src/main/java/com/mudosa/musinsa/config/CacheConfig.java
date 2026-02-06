package com.mudosa.musinsa.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mudosa.musinsa.order.application.dto.OrderItem;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, List<OrderItem>> orderItemsLocalCache(MeterRegistry meterRegistry) {
        Cache<String, List<OrderItem>> cache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
        
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "orderItemsLocalCache");
        return cache;
    }
}
