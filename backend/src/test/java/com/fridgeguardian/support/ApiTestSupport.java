package com.fridgeguardian.support;

import com.fridgeguardian.user.UserAccount;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** 통합 테스트 공용 도우미. */
public final class ApiTestSupport {
    private ApiTestSupport() { }

    /** 서명 검증을 통과한 Access Token이 있는 것처럼 sub에 사용자 ID를 담은 인증을 만듭니다. */
    public static RequestPostProcessor asUser(UserAccount user) {
        return jwt().jwt(token -> token.subject(String.valueOf(user.getId())));
    }

    /** 식재료 생성 요청 JSON. expirationDate가 null이면 필드를 생략합니다. */
    public static String ingredientJson(String name, String category, String unit, String quantity,
                                        String purchaseDate, String expirationDate, String storage) {
        return "{\"name\":\"" + name + "\",\"category\":\"" + category + "\",\"quantity\":" + quantity
            + ",\"unit\":\"" + unit + "\",\"purchaseDate\":\"" + purchaseDate + "\","
            + (expirationDate == null ? "" : "\"expirationDate\":\"" + expirationDate + "\",")
            + "\"storageType\":\"" + storage + "\"}";
    }
}
