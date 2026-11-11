package com.fridgeguardian.user.preference;

import java.util.List;

/** 추천과 AI 레시피 생성에 적용하는 개인화 조건입니다. */
public record UserFilter(int spicyLevel, List<String> allergyNames, List<String> dislikeNames,
                         ExclusionMatcher allergies, ExclusionMatcher dislikes) {
    public static UserFilter none() {
        return new UserFilter(UserPreference.UNRESTRICTED, List.of(), List.of(), ExclusionMatcher.none(), ExclusionMatcher.none());
    }

    /** AI 프롬프트에 '사용 금지 재료'로 전달할 이름들(알레르기 + 비선호). */
    public List<String> forbiddenNames() {
        return java.util.stream.Stream.concat(allergyNames.stream(), dislikeNames.stream()).distinct().toList();
    }
}
