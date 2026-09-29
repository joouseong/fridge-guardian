package com.fridgegatekeeper.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppClockConfiguration {
    /** 서버 위치에 관계없이 한국 날짜를 사용하고, 테스트에서는 고정 Clock으로 바꿀 수 있습니다. */
    @Bean
    public Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
}
