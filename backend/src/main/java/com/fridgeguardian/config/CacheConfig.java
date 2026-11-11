package com.fridgeguardian.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * 캐시 대상은 사용자와 무관한 공통 데이터(레시피 목록)와 재료 조합이 같으면 재사용할 수 있는 AI 결과뿐입니다.
 * 사용자 재고·추천 결과는 항상 최신·격리 상태여야 하므로 캐시하지 않습니다.
 * CACHE_TYPE=redis 이면 Redis를, 기본값(caffeine)이면 프로세스 메모리를 사용합니다.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);
    public static final String AI_RECIPES = "aiRecipes";

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    RedisCacheManagerBuilderCustomizer redisTtl() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig().disableCachingNullValues();
        return builder -> builder
            .cacheDefaults(base.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("recipes", base.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration(AI_RECIPES, base.entryTtl(Duration.ofHours(24)));
    }

    /** Redis가 잠시 죽어도 요청은 캐시 없이(DB 조회로) 계속 처리합니다. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException e, Cache cache, Object key) { warn("조회", cache, e); }
            @Override public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) { warn("저장", cache, e); }
            @Override public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) { warn("삭제", cache, e); }
            @Override public void handleCacheClearError(RuntimeException e, Cache cache) { warn("비우기", cache, e); }
            private void warn(String action, Cache cache, RuntimeException e) {
                log.warn("캐시 {} 실패({}): {}", action, cache.getName(), e.getClass().getSimpleName());
            }
        };
    }
}
