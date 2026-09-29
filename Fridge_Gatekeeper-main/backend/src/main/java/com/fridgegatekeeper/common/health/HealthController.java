package com.fridgegatekeeper.common.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 백엔드가 HTTP 요청에 응답하는지 확인하는 최소한의 API입니다.
 * DB 연결 상태나 사용자 로그인 상태를 검사하는 API는 아닙니다.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    // 브라우저에서 GET /api/health를 요청하면 반환 객체가 JSON으로 변환됩니다.
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", "fridge-gatekeeper");
    }

    // record는 값 전달에 사용하는 간단한 불변 객체입니다.
    public record HealthResponse(String status, String service) {
    }
}

