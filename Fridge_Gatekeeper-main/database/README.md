# STEP 2 — MySQL 테이블 설계

MySQL 8, UTF-8, 날짜 기준 Asia/Seoul을 사용합니다. 현재 Windows 검증 DB는 8.0.44이고 Compose 구성은 8.4입니다. Flyway의 V1__initial_schema.sql로 스키마를 생성하고 후속 마이그레이션을 적용하며 Hibernate validate로 Entity와의 일치를 검사합니다.

| 테이블 | 컬럼 | 설명 |
| --- | --- | --- |
| users | id BIGINT PK, email VARCHAR(254) UNIQUE, password VARCHAR(100), nickname VARCHAR(30) | 이메일 소문자 정규화, 비밀번호 BCrypt 해시 |
| ingredients | id BIGINT PK, user_id BIGINT FK, name VARCHAR(80), category VARCHAR(20), quantity DECIMAL(12,3), unit VARCHAR(20), purchase_date DATE, expiration_date DATE, storage_type VARCHAR(20), version BIGINT | 양수 수량, 구매일 ≤ 만료일, 수정 충돌 감지 |
| recipes | id BIGINT PK, name VARCHAR(100) UNIQUE, description VARCHAR(500), cooking_time INT, difficulty VARCHAR(20), servings INT, instructions TEXT, calories INT, protein/carbs/fat DECIMAL(8,2) | 기본 인분, 줄바꿈으로 나눈 조리 순서, 1인분당 영양 추정값 |
| recipe_ingredients | id BIGINT PK, recipe_id BIGINT FK, ingredient_name VARCHAR(80), required_quantity DECIMAL(12,3), unit VARCHAR(20) | 기본 인분 기준 필요 수량, (recipe_id, ingredient_name) UNIQUE |
| ingredient_batch_requests | id BIGINT PK, user_id BIGINT FK, request_key VARCHAR(36), payload_hash VARCHAR(64), response_json LONGTEXT | (user_id, request_key) UNIQUE, 완료한 일괄 등록의 중복 방지 기록 |

관계는 users 1:N ingredients, users 1:N ingredient_batch_requests, recipes 1:N recipe_ingredients입니다. 부모 삭제 시 자식도 삭제합니다.
재고 조회를 위해 (user_id, expiration_date) 인덱스를 둡니다.

카테고리: MEAT, VEGETABLE, FRUIT, DAIRY, FROZEN, SEASONING, GRAIN, SEAFOOD, OTHER.
보관: FRIDGE(냉장), FREEZER(냉동), PANTRY(실온).
단위: PIECE(개), GRAM(g), KILOGRAM(kg), MILLILITER(ml), LITER(L), PACK(팩), BAG(봉), BLOCK(모), BUNCH(단).

유통기한 상태는 저장하지 않고 매 요청에서 계산합니다. 오늘 이전은 EXPIRED, 오늘~3일 뒤는 SOON, 이후는 SAFE입니다.
V3__optional_ingredient_expiration.sql은 기존 데이터와 V1·V2를 유지하면서 ingredients.expiration_date만 NULL 허용으로 변경합니다. NULL은 UNKNOWN(기한 미등록)이며 구매일 비교 제약은 입력된 기한에만 적용됩니다. API의 daysUntilExpiration도 이 경우 null이고, 날짜를 임의로 추정하지 않습니다.
SAFE는 날짜 기준 분류이며 실제 식품 안전을 보증하지 않습니다. 단위·인분·조리법·영양값은 요청 기능을 지원하기 위한 확장 컬럼입니다.

V4__ingredient_batch_requests.sql은 일괄 등록의 완료 기록을 추가합니다. 재고와 기록을 같은 트랜잭션에 저장하며 동일 사용자 행을 잠가 동시 요청의 중복을 방지합니다. SHA-256 해시로 동일 번호의 내용 변경을 검출하고 응답 JSON은 최초 등록 시점의 스냅샷으로 보관합니다. 이 테이블은 추천 재고에 합산하지 않습니다. 현재 자동 정리 정책은 없으며 운영 시 보관 기간을 별도로 정해야 합니다.
