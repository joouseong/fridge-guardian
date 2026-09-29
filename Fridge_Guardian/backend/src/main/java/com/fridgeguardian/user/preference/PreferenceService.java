package com.fridgeguardian.user.preference;

import com.fridgeguardian.recommendation.IngredientNames;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {
    private final UserPreferenceRepository preferences;
    private final ExcludedItemRepository excluded;

    public PreferenceService(UserPreferenceRepository preferences, ExcludedItemRepository excluded) {
        this.preferences = preferences; this.excluded = excluded;
    }

    @Transactional(readOnly = true)
    public PreferenceDtos.Response get(Long userId) {
        UserFilter filter = filterOf(userId);
        return new PreferenceDtos.Response(filter.spicyLevel(), filter.allergyNames(), filter.dislikeNames());
    }

    @Transactional
    public PreferenceDtos.Response update(Long userId, PreferenceDtos.Request request) {
        UserPreference preference = preferences.findById(userId).orElseGet(() -> new UserPreference(userId, request.spicyLevel()));
        preference.setSpicyLevel(request.spicyLevel());
        preferences.save(preference);
        excluded.deleteAllByUserId(userId);
        List<String> allergies = distinct(request.allergies());
        List<String> dislikes = distinct(request.dislikes());
        allergies.forEach(name -> excluded.save(new ExcludedItem(userId, ExcludedItem.Type.ALLERGY, name)));
        dislikes.forEach(name -> excluded.save(new ExcludedItem(userId, ExcludedItem.Type.DISLIKE, name)));
        excluded.flush();
        return new PreferenceDtos.Response(request.spicyLevel(), allergies, dislikes);
    }

    /** 추천·AI 생성이 사용하는 개인화 조건. 설정한 적이 없으면 아무것도 걸러 내지 않습니다. */
    @Transactional(readOnly = true)
    public UserFilter filterOf(Long userId) {
        int spicy = preferences.findById(userId).map(UserPreference::getSpicyLevel).orElse(UserPreference.UNRESTRICTED);
        List<ExcludedItem> items = excluded.findAllByUserIdOrderByIdAsc(userId);
        List<String> allergies = items.stream().filter(i -> i.getType() == ExcludedItem.Type.ALLERGY).map(ExcludedItem::getName).toList();
        List<String> dislikes = items.stream().filter(i -> i.getType() == ExcludedItem.Type.DISLIKE).map(ExcludedItem::getName).toList();
        return new UserFilter(spicy, allergies, dislikes, ExclusionMatcher.of(allergies), ExclusionMatcher.of(dislikes));
    }

    /** '달걀'과 '계란'처럼 같은 재료로 인식되는 이름은 처음 입력한 표기 하나만 저장합니다. */
    private static List<String> distinct(List<String> names) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String name : names) unique.putIfAbsent(IngredientNames.normalize(name), name.strip());
        return List.copyOf(unique.values());
    }
}
