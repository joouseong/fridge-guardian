package com.fridgeguardian.ai;

import java.util.Map;

/**
 * 레시피 생성에 쓰는 LLM 호출 추상화. 지금은 OpenAI 구현(OpenAiClient)만 있으며,
 * Gemini 등은 같은 인터페이스를 구현한 빈을 추가하면 서비스 코드를 바꾸지 않고 교체할 수 있습니다.
 */
public interface LlmClient {
    boolean available();
    String model();

    /** 스키마에 맞춘 JSON 문자열을 돌려줍니다. 값의 안전성은 호출한 쪽에서 다시 검증해야 합니다. */
    String generateJson(String instructions, String inputJson, String schemaName, Map<String, Object> schema);
}
