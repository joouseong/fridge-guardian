package com.fridgegatekeeper.chat;

import com.fridgegatekeeper.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 단일 서버의 유료 요청 제한. 실패한 외부 호출도 집계하며 서버 재시작 시 초기화됩니다. */
@Component
public class AiRequestLimiter {
    private final Clock clock;
    private final int perUser;
    private final int perMinute;
    private final int perDay;
    private final int concurrent;
    private final Map<Long, ArrayDeque<Instant>> users = new HashMap<>();
    private final ArrayDeque<Instant> recent = new ArrayDeque<>();
    private final Set<Long> active = new HashSet<>();
    private LocalDate day;
    private int dailyCount;

    public AiRequestLimiter(Clock clock,
                            @Value("${app.openai.limits.per-user-per-minute:5}") int perUser,
                            @Value("${app.openai.limits.per-minute:20}") int perMinute,
                            @Value("${app.openai.limits.per-day:100}") int perDay,
                            @Value("${app.openai.limits.concurrent:2}") int concurrent) {
        if (perUser < 1 || perMinute < 1 || perDay < 1 || concurrent < 1) {
            throw new IllegalArgumentException("AI request limits must be positive.");
        }
        this.clock = clock;
        this.perUser = perUser;
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.concurrent = concurrent;
    }

    public synchronized Permit acquire(Long userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(60);
        trim(recent, cutoff);
        users.values().forEach(times -> trim(times, cutoff));
        users.values().removeIf(ArrayDeque::isEmpty);
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        if (!today.equals(day)) {
            day = today;
            dailyCount = 0;
        }
        if (active.contains(userId)) {
            throw limited("AI_REQUEST_IN_PROGRESS", "이미 AI 답변을 만들고 있어요. 잠시 기다린 뒤 다시 시도해 주세요.");
        }
        if (dailyCount >= perDay) {
            throw limited("AI_DAILY_LIMIT", "오늘의 AI 요청 한도에 도달했어요. 기본 추천을 이용하거나 내일 다시 시도해 주세요.");
        }
        if (active.size() >= concurrent) {
            throw limited("AI_BUSY", "현재 AI 요청이 많아요. 잠시 뒤 다시 시도하거나 기본 추천을 이용해 주세요.");
        }
        ArrayDeque<Instant> user = users.get(userId);
        if (recent.size() >= perMinute || (user != null && user.size() >= perUser)) {
            throw limited("AI_REQUEST_LIMIT", "AI 요청을 너무 빠르게 보내셨어요. 1분 뒤 다시 시도하거나 기본 추천을 이용해 주세요.");
        }
        users.computeIfAbsent(userId, ignored -> new ArrayDeque<>()).addLast(now);
        recent.addLast(now);
        dailyCount++;
        active.add(userId);
        return new Permit(userId);
    }

    private static void trim(ArrayDeque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && !times.getFirst().isAfter(cutoff)) times.removeFirst();
    }

    private static ApiException limited(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public final class Permit implements AutoCloseable {
        private final Long userId;
        private boolean released;
        private Permit(Long userId) { this.userId = userId; }
        @Override public void close() {
            synchronized (AiRequestLimiter.this) {
                if (!released) {
                    active.remove(userId);
                    released = true;
                }
            }
        }
    }
}
