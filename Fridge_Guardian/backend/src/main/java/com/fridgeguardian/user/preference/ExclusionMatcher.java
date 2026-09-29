package com.fridgeguardian.user.preference;

import com.fridgeguardian.recommendation.IngredientNames;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 알레르기·비선호 이름과 재료 이름을 비교합니다. 이름 기반 규칙이라 모든 식품 성분을 보장하지 않습니다.
 *
 * 사용자가 대표어(예: '우유', '갑각류', '해산물')를 입력하면 그에 속한 재료(치즈·버터, 새우·게, 생선·조개…)도 함께 걸러 내고,
 * 구체적인 이름(예: '새우')을 입력하면 그 이름과 그 이름이 들어간 재료('새우젓')만 걸러 냅니다.
 * 확장은 대표어 → 하위 재료 한 방향으로만 일어나므로 '새우' 알레르기가 다른 해산물까지 제외하지 않습니다.
 */
public final class ExclusionMatcher {
    private record Group(Set<String> triggers, Set<String> terms) { }

    private static final Set<String> SHELLFISH = Set.of("새우", "게", "꽃게", "랍스터", "크랩");
    private static final Set<String> FISH = Set.of("생선", "고등어", "갈치", "연어", "멸치", "명태", "참치");
    private static final Set<String> MOLLUSKS = Set.of("조개", "홍합", "굴", "전복", "바지락");

    private static final List<Group> GROUPS = List.of(
        new Group(Set.of("우유", "유제품", "락토스", "유당"),
            Set.of("우유", "유제품", "치즈", "요거트", "요구르트", "버터", "크림", "생크림", "분유")),
        new Group(Set.of("계란", "달걀", "난류"), Set.of("계란", "달걀", "마요네즈")),
        new Group(Set.of("밀", "밀가루", "글루텐"),
            Set.of("밀", "밀가루", "파스타면", "국수", "소면", "우동", "라면", "빵", "만두", "튀김가루", "부침가루")),
        new Group(Set.of("대두", "콩", "콩류"), Set.of("대두", "콩", "두부", "두유", "된장", "청국장", "간장")),
        new Group(Set.of("땅콩"), Set.of("땅콩", "땅콩버터")),
        new Group(Set.of("견과류", "견과"), Set.of("견과류", "호두", "아몬드", "잣", "캐슈넛", "땅콩")),
        new Group(Set.of("갑각류"), union(Set.of("갑각류"), SHELLFISH)),
        new Group(Set.of("생선"), FISH),
        new Group(Set.of("조개류", "패류"), union(Set.of("조개류"), MOLLUSKS)),
        new Group(Set.of("해산물", "수산물"), union(Set.of("해산물", "오징어", "문어"), SHELLFISH, FISH, MOLLUSKS))
    );
    private static final ExclusionMatcher EMPTY = new ExclusionMatcher(Set.of());
    private final Set<String> terms;

    private ExclusionMatcher(Set<String> terms) { this.terms = terms; }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> all = new LinkedHashSet<>();
        for (Set<String> set : sets) all.addAll(set);
        return Set.copyOf(all);
    }

    public static ExclusionMatcher none() { return EMPTY; }

    public static ExclusionMatcher of(Collection<String> names) {
        Set<String> terms = new LinkedHashSet<>();
        for (String name : names) {
            String key = IngredientNames.normalize(name);
            if (key.isEmpty()) continue;
            terms.add(key);
            for (Group group : GROUPS) {
                if (group.triggers().stream().anyMatch(trigger -> IngredientNames.normalize(trigger).equals(key))) {
                    group.terms().forEach(term -> terms.add(IngredientNames.normalize(term)));
                }
            }
        }
        return new ExclusionMatcher(Set.copyOf(terms));
    }

    public boolean isEmpty() { return terms.isEmpty(); }

    /** 재료 이름에 걸러야 할 이름이 하나라도 들어 있으면 true입니다. */
    public boolean matches(String ingredientName) {
        if (terms.isEmpty() || ingredientName == null) return false;
        String normalized = IngredientNames.normalize(ingredientName);
        return terms.stream().anyMatch(normalized::contains);
    }

    /**
     * 조리 순서·제목 같은 문장용입니다. '게', '밀', '굴'처럼 한 글자 이름은 '얇게', '밀어' 같은 일반 단어와
     * 구분할 수 없으므로 두 글자 이상인 이름만 비교합니다. (재료 이름은 matches를 사용하세요.)
     */
    public boolean matchesText(String text) {
        if (terms.isEmpty() || text == null) return false;
        String normalized = IngredientNames.normalize(text);
        return terms.stream().anyMatch(term -> term.length() >= 2 && normalized.contains(term));
    }
}
