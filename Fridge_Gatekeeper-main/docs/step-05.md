# STEP 5 — 유통기한 관리

## 1. 구현 목적

사용자가 냉장고를 일일이 살펴보지 않아도 오늘까지인 재료, 3일 이내 임박 재료, 이미 만료된 재료를 바로 확인하도록 집계합니다.

## 2. 생성 파일

- [AppClockConfiguration.java](../backend/src/main/java/com/fridgegatekeeper/common/AppClockConfiguration.java): 한국 기준 시계
- [ExpiryStatus.java](../backend/src/main/java/com/fridgegatekeeper/ingredient/ExpiryStatus.java): 상태 경계 계산
- [DashboardResponse.java](../backend/src/main/java/com/fridgegatekeeper/ingredient/DashboardResponse.java): 합계와 목록 DTO
- [DashboardService.java](../backend/src/main/java/com/fridgegatekeeper/ingredient/DashboardService.java): 사용자별 집계
- [DashboardController.java](../backend/src/main/java/com/fridgegatekeeper/ingredient/DashboardController.java): 조회 API

## 3. 전체 코드

위 파일 링크가 전체 실행 코드입니다. 식재료 목록과 대시보드는 같은 `ExpiryStatus.of`를 사용하므로 상태 기준이 일치합니다.

## 4. 코드 설명

오늘은 서버 운영체제 시간대와 관계없이 `Asia/Seoul` 날짜로 계산합니다. `Clock`을 주입하므로 테스트에서 특정 시각을 고정할 수 있습니다.

| 조건 | 상태 | 화면 표현 |
| --- | --- | --- |
| 유통기한 < 오늘 | `EXPIRED` | 만료, 빨강 |
| 오늘 ≤ 유통기한 ≤ 오늘 + 3일 | `SOON` | 임박, 주황 |
| 오늘 + 3일 < 유통기한 | `SAFE` | 안전, 초록 |

`GET /api/dashboard`는 `total`, `safeCount`, `soonCount`, `expiredCount`, `todayCount`, `expiringIngredients`, `expiredIngredients`, `today`를 반환합니다. `todayCount`는 임박 합계에 포함되는 부분집합입니다. 만료 재료는 임박 목록에 섞지 않습니다. 일수는 날짜 차이이므로 오늘은 0, 어제는 -1입니다.

여기서 안전 표시는 입력한 날짜를 기준으로 한 상태입니다. 실제 섭취 가능 여부를 확인하는 기능은 아닙니다. 추천 기능은 만료 재료를 보유 재료로 사용하지 않습니다.

## 5. 실행 방법

로그인 후 어제·오늘·3일 후·4일 후 유통기한의 재료를 등록하고 대시보드를 확인합니다. 각 상태가 만료·임박·임박·안전으로 나와야 합니다. 빈 냉장고는 모든 개수가 0이고 목록은 빈 배열입니다. 자동 날짜 경계 검증은 `backend`에서 `./mvnw.cmd test`를 실행합니다.
