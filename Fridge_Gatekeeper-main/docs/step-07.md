# STEP 7 — 재고 기반 추천 알고리즘

## 구현 목적
현재 냉장고에서 활용할 재료가 많은 요리를 먼저 제안하고 임박 재료의 소비를 돕습니다.

## 파일과 전체 코드
- [추천 서비스](../backend/src/main/java/com/fridgegatekeeper/recommendation/RecommendationService.java)
- [식재료명 정규화](../backend/src/main/java/com/fridgegatekeeper/recommendation/IngredientNames.java)
- [단위 변환](../backend/src/main/java/com/fridgegatekeeper/recommendation/Quantities.java)
- [응답 DTO](../backend/src/main/java/com/fridgegatekeeper/recommendation/RecipeRecommendation.java)
- [조회 API](../backend/src/main/java/com/fridgegatekeeper/recipe/RecipeController.java)

## 코드 설명
보유 재료 활용 종류 수 내림차순 → 임박 재료 활용 종류 수 내림차순 → 부족 재료 종류 수 오름차순 → ID 순으로 정렬합니다.
우선순위가 뒤집힐 수 있는 가중치 합산 방식을 사용하지 않습니다.
같은 재료의 유효한 재고 수량을 합산하며 만료 재료는 제외합니다. 1·2인분에 비례해 필요량을 조절합니다.
kg↔g, L↔ml 변환을 지원합니다. 팩↔g처럼 알 수 없는 환산은 하지 않고 단위 불일치를 응답합니다.
일부 수량만 있어도 활용 재료에 포함하되 부족분을 별도로 표시합니다.
날짜 기준 SAFE는 식품의 실제 안전성을 보증하지 않습니다. 상태가 이상한 식재료는 사용하지 않아야 합니다.

## 실행 방법
로그인 후 GET /api/recipes/recommendations?servings=2 및 /api/recipes/{id}?servings=1로 조회합니다.
계란, 대파, 김치, 돼지고기를 등록하면 해당 재료를 활용하는 레시피의 순위가 올라갑니다.
