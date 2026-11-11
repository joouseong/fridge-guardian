package com.fridgeguardian.batch;

import com.fridgeguardian.user.token.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 자정(한국 시간)에 유통기한 알림 배치를 시작합니다.
 * 서버가 여러 대이면 대수만큼 실행되지만, 같은 날짜의 JobInstance는 한 번만 성공하므로 알림은 중복되지 않습니다.
 * (여러 대를 안정적으로 운영하려면 ShedLock 같은 분산 잠금이나 스케줄러 전용 인스턴스를 검토하세요.)
 */
@Component
@ConditionalOnProperty(name = "app.batch.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);
    private final ExpiryJobRunner runner;
    private final RefreshTokenService refreshTokens;

    public ExpiryScheduler(ExpiryJobRunner runner, RefreshTokenService refreshTokens) {
        this.runner = runner; this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "${app.batch.expiry-cron}", zone = "Asia/Seoul")
    public void nightly() { runner.runToday(); }

    /** 만료된 Refresh Token 기록을 매일 새벽에 정리합니다. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void purgeRefreshTokens() {
        log.info("만료된 Refresh Token {}건을 정리했습니다.", refreshTokens.purgeExpired());
    }
}
