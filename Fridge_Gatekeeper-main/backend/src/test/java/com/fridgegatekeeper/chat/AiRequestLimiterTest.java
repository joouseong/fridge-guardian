package com.fridgegatekeeper.chat;

import com.fridgegatekeeper.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiRequestLimiterTest {
    private final Clock clock = mock(Clock.class);
    private final Instant initial = Instant.parse("2026-09-16T14:59:00Z");

    @BeforeEach void setClock() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(initial);
    }

    @Test void rollingMinuteEnforcesPerUserAndGlobalLimitsAndExpiresAtSixtySeconds() {
        var limiter = new AiRequestLimiter(clock, 1, 2, 20, 2);
        limiter.acquire(1L).close();
        rejected(limiter, 1L, "AI_REQUEST_LIMIT");
        limiter.acquire(2L).close();
        rejected(limiter, 3L, "AI_REQUEST_LIMIT");
        when(clock.instant()).thenReturn(initial.plusSeconds(59));
        rejected(limiter, 1L, "AI_REQUEST_LIMIT");
        when(clock.instant()).thenReturn(initial.plusSeconds(60));
        limiter.acquire(1L).close();
    }

    @Test void dailyLimitResetsAtKoreanMidnightAndRejectedAttemptsDoNotCount() {
        var limiter = new AiRequestLimiter(clock, 1, 10, 2, 2);
        limiter.acquire(1L).close();
        rejected(limiter, 1L, "AI_REQUEST_LIMIT");
        limiter.acquire(2L).close();
        rejected(limiter, 3L, "AI_DAILY_LIMIT");
        when(clock.instant()).thenReturn(Instant.parse("2026-09-16T15:00:00Z"));
        limiter.acquire(3L).close();
    }

    @Test void sameUserCannotOverlapAndOldPermitCannotReleaseNewRequest() {
        var limiter = new AiRequestLimiter(clock, 10, 20, 100, 1);
        var first = limiter.acquire(1L);
        rejected(limiter, 1L, "AI_REQUEST_IN_PROGRESS");
        rejected(limiter, 2L, "AI_BUSY");
        first.close();
        var second = limiter.acquire(1L);
        first.close();
        rejected(limiter, 2L, "AI_BUSY");
        second.close();
        limiter.acquire(2L).close();
    }

    @Test void concurrentRequestsCannotRacePastGlobalLimit() throws Exception {
        var limiter = new AiRequestLimiter(clock, 20, 3, 100, 20);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(10)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (long id = 0; id < 10; id++) {
                long userId = id;
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        try (var permit = limiter.acquire(userId)) { successes.incrementAndGet(); }
                    } catch (ApiException error) {
                        assertThat(error.getCode()).isEqualTo("AI_REQUEST_LIMIT");
                    } catch (InterruptedException error) { throw new RuntimeException(error); }
                }));
            }
            start.countDown();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        }
        assertThat(successes.get()).isEqualTo(3);
    }

    private void rejected(AiRequestLimiter limiter, Long user, String code) {
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(error.getCode()).isEqualTo(code);
        });
    }
}
