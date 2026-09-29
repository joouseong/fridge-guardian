package com.fridgeguardian.config;

import com.fridgeguardian.recipe.RecipeCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

/** CACHE_TYPE=redis 이지만 Redis 서버가 없는 상황에서도 요청이 DB 조회로 계속 처리되는지 검증합니다. */
@SpringBootTest(properties = {
    "spring.cache.type=redis",
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=6399",          // 아무도 듣지 않는 포트
    "spring.data.redis.connect-timeout=300ms",
    "spring.data.redis.timeout=300ms"})
@ActiveProfiles("test")
class RedisCacheFallbackTest {
    @Autowired CacheManager caches;
    @Autowired RecipeCatalog catalog;

    @Test void redisCacheManagerIsUsedButAnOutageDoesNotBreakReads() {
        assertThat(caches).isInstanceOf(RedisCacheManager.class);
        // Redis에 연결할 수 없어도 캐시 오류를 삼키고 DB의 레시피를 그대로 돌려줍니다.
        assertThat(catalog.all()).hasSize(21);
        assertThat(catalog.get(16L).name()).isEqualTo("간장 계란밥");
        assertThat(catalog.get(16L).ingredients()).isNotEmpty();
    }

    /** 실제 Redis 없이도, Redis 캐시가 쓰는 JDK 직렬화로 레시피 스냅샷이 손실 없이 왕복하는지 확인합니다. */
    @Test void recipeSnapshotsSurviveRedisJdkSerialization() {
        var serializer = new org.springframework.data.redis.serializer.JdkSerializationRedisSerializer();
        var all = catalog.all();
        assertThat(serializer.deserialize(serializer.serialize(all))).isEqualTo(all);
    }
}
