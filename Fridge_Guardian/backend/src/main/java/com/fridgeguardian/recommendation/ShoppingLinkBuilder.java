package com.fridgeguardian.recommendation;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 재료 이름으로 쿠팡·마켓컬리 검색 주소를 만듭니다. 순수 문자열 조합이라 쇼핑몰이 주소 형식을 바꿔도
 * 앱 기능에는 영향이 없고 링크만 어긋날 수 있습니다. (제휴·딥링크가 필요하면 별도 협의가 필요합니다.)
 */
@Component
public class ShoppingLinkBuilder {
    /** 부족한 재료가 이 개수 이하일 때만 링크를 보여 줍니다. (PRD: 부족한 재료 1~2개) */
    public static final int MAX_MISSING_FOR_LINKS = 2;

    public List<ShoppingLink> forNames(List<String> names) {
        return names.stream().flatMap(name -> {
            String query = URLEncoder.encode(name, StandardCharsets.UTF_8);
            return Stream.of(
                new ShoppingLink(name, "쿠팡", "https://www.coupang.com/np/search?q=" + query),
                new ShoppingLink(name, "마켓컬리", "https://www.kurly.com/search?sword=" + query));
        }).toList();
    }

    public List<ShoppingLink> forMissing(List<String> missingNames) {
        if (missingNames.isEmpty() || missingNames.size() > MAX_MISSING_FOR_LINKS) return List.of();
        return forNames(missingNames);
    }
}
