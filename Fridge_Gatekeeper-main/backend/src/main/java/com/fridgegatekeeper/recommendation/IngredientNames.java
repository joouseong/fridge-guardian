package com.fridgegatekeeper.recommendation;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

public final class IngredientNames {
    private IngredientNames() {}
    private static final Map<String,String> ALIASES = Map.ofEntries(
        Map.entry("달걀","계란"), Map.entry("파","대파"), Map.entry("냉동만두","만두"),
        Map.entry("쌀밥","밥"), Map.entry("흰쌀밥","밥"), Map.entry("배추김치","김치"),
        Map.entry("신김치","김치"), Map.entry("진간장","간장"), Map.entry("양조간장","간장"),
        Map.entry("양송이버섯","버섯"), Map.entry("표고버섯","버섯"), Map.entry("플레인요거트","요거트")
    );
    public static String normalize(String name) {
        String key=Normalizer.normalize(name,Normalizer.Form.NFKC)
            .replaceAll("[\\s\\p{Z}]+","").toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(key,key);
    }
}
