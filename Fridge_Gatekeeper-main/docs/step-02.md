# STEP 2 — DB 설계 및 Entity

## 구현 목적
회원별 재고와 공통 레시피를 영속 저장합니다. 테이블 설계를 먼저 작성한 뒤 이에 맞는 Entity를 만들었습니다.

## 파일과 전체 코드
- [테이블 설계](../database/README.md)
- [최초 스키마](../backend/src/main/resources/db/migration/V1__initial_schema.sql)
- [UserAccount](../backend/src/main/java/com/fridgegatekeeper/user/UserAccount.java)
- [Ingredient](../backend/src/main/java/com/fridgegatekeeper/ingredient/Ingredient.java)
- [Recipe](../backend/src/main/java/com/fridgegatekeeper/recipe/Recipe.java)
- [RecipeIngredient](../backend/src/main/java/com/fridgegatekeeper/recipe/RecipeIngredient.java)
- [MySQL 실행 설정](../compose.yaml), [애플리케이션 설정](../backend/src/main/resources/application.yml)

## 코드 설명
@Entity는 Java 객체와 테이블을 연결하고 @ManyToOne은 외래 키 관계를 나타냅니다.
수량은 소수 오차를 줄이기 위해 BigDecimal을 사용합니다. @Version은 동시에 같은 재고를 수정할 때 충돌을 감지합니다.
Flyway는 실행한 SQL 버전을 기록하여 재시작할 때 테이블을 재생성하지 않습니다.
JPA의 ddl-auto=validate는 스키마 불일치를 발견하면 실행을 중단합니다.
DB에는 비밀번호 원문 대신 해시를 저장하며 Entity를 직접 HTTP 응답으로 반환하지 않습니다.

## 실행 방법
프로젝트 루트에서 docker compose up -d mysql을 실행한 뒤 backend에서 .\\mvnw.cmd spring-boot:run을 실행합니다.
DB_URL, DB_USERNAME, DB_PASSWORD는 서버 실행 환경 변수로 변경할 수 있습니다.
현재 PC의 Docker 가상화 미지원 환경에는 .local/에 격리된 실제 MySQL이 준비되어 있습니다. 자세한 명령은 최종 README를 참고하세요.
자동 테스트만 application-test.yml의 H2 MySQL 모드를 사용합니다. 기본 실행 DB는 MySQL입니다.
