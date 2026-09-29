# STEP 1 — 프로젝트 구조 생성

> 이 문서는 STEP 1 당시의 구현 기록입니다. 현재 전체 MVP 실행에는 MySQL이 필요하므로 [최신 실행 안내](../README.md)를 따르세요.

## 1. 구현 목적

백엔드와 프론트엔드가 각자 실행되는 최소 기반을 준비합니다.
백엔드는 HTTP 요청에 JSON으로 응답하고, 프론트엔드는 React 시작 화면을 표시합니다.
이를 먼저 확인하면 이후 기능을 추가할 때 환경 문제와 기능 문제를 구분하기 쉽습니다.

현재 단계에는 DB 연결과 업무 기능이 없습니다. 시작 화면의 설명은 서비스의 개발 방향을 나타냅니다.
냉장고 데이터 조회나 추천 결과가 아니며, 프론트엔드의 API 호출은 STEP 9에서 연결합니다.

Java 21과 Spring Boot 4.1.1, React와 Vite를 사용합니다.
React 코드는 입문자가 접근하기 쉬운 JavaScript로 작성합니다.
Java 21은 선택한 [Spring Boot의 지원 범위](https://docs.spring.io/spring-boot/system-requirements.html)에 포함됩니다.
프론트엔드 구동 환경은 [Vite 공식 실행 요구 사항](https://vite.dev/guide/)을 확인하고 Node.js 24 LTS를 기준으로 준비했습니다.

## 2. 생성한 파일

[전체 폴더 구조](../README.md#구조와-단계별-코드-설명)를 먼저 확인하세요.

| 파일 또는 폴더 | 목적 |
| --- | --- |
| `backend/pom.xml` | Spring Boot 의존성과 Java 버전을 선언 |
| `backend/mvnw`, `backend/mvnw.cmd` | 같은 Maven 버전으로 프로젝트 실행 |
| `backend/src/main/java/com/fridgegatekeeper/FridgeGatekeeperApplication.java` | Spring Boot 시작점 |
| `backend/src/main/java/com/fridgegatekeeper/common/health/HealthController.java` | 서버 실행을 확인하는 GET API |
| `backend/src/main/resources/application.yml` | 애플리케이션 이름과 서버 포트 |
| `backend/src/test/` | Spring Initializr에서 제공한 기본 컨텍스트 실행 테스트 |
| `frontend/package.json`, `frontend/package-lock.json` | 프론트엔드 명령과 고정된 의존성 |
| `frontend/vite.config.js` | React 빌드 설정 및 개발 서버 포트 |
| `frontend/index.html`, `frontend/src/main.jsx` | HTML 문서와 React 실행 시작점 |
| `frontend/src/App.jsx`, `frontend/src/pages/` | 시작 화면 구성 |
| `frontend/src/styles/` | 화면 크기에 대응하는 기본 스타일 |
| `database/README.md` | 다음 단계에서 작성할 DB 설계의 위치 |
| `.gitignore`, `.gitattributes`, `.editorconfig` | 소스 관리와 편집 규칙 |

## 3. 전체 코드 위치

코드는 설명용 일부 조각이 아니라 실행 가능한 파일로 작성했습니다.
아래 링크에서 원본 전체를 확인할 수 있습니다.

- [백엔드 빌드 설정](../backend/pom.xml)
- [백엔드 실행 코드](../backend/src/main/java/com/fridgegatekeeper/FridgeGatekeeperApplication.java)
- [상태 확인 API 전체 코드](../backend/src/main/java/com/fridgegatekeeper/common/health/HealthController.java)
- [서버 설정](../backend/src/main/resources/application.yml)
- [프론트엔드 의존성 및 실행 명령](../frontend/package.json)
- [Vite 설정](../frontend/vite.config.js)
- [React 실행 코드](../frontend/src/main.jsx)
- [최상위 React 컴포넌트](../frontend/src/App.jsx)
- [React 시작 화면](../frontend/src/pages/WelcomePage.jsx)
- [공통 브랜드 아이콘](../frontend/src/components/BrandMark.jsx)
- [반응형 스타일](../frontend/src/styles/global.css)
- [HTML 진입점](../frontend/index.html)
- [ESLint 설정](../frontend/eslint.config.js)

## 4. 코드 설명

### 백엔드의 요청 흐름

```text
브라우저 GET /api/health
    → Spring Boot 내장 웹 서버
    → HealthController.health()
    → HealthResponse 객체
    → JSON 응답
```

`@SpringBootApplication`은 Spring Boot의 설정과 컴포넌트 검색을 활성화합니다.
기능 코드는 `com.fridgegatekeeper` 아래에 두어 자동 검색 대상이 되게 합니다.
`SpringApplication.run(...)`이 애플리케이션과 내장 웹 서버를 시작합니다.

`@RestController`는 메서드의 반환값을 HTTP 응답 본문으로 보내는 컨트롤러입니다.
`@RequestMapping("/api/health")`는 이 컨트롤러의 URL을,
`@GetMapping`은 HTTP GET 요청을 처리할 메서드를 지정합니다.
반환하는 `HealthResponse`는 Java의 `record` 문법으로 만든 불변 데이터 객체입니다.
Spring이 이를 `{"status":"UP","service":"fridge-gatekeeper"}` 형태의 JSON으로 변환합니다.
이 API는 웹 서버의 응답만 확인하며 DB나 AI 연결 상태까지 의미하지 않습니다.

`application.yml`의 `${SERVER_PORT:8080}`은 `SERVER_PORT` 환경 변수가 있으면 그 값을,
없으면 `8080`을 사용한다는 뜻입니다.

기능은 `user`, `ingredient`, `recipe`, `recommendation`, `ai` 패키지로 나누어 추가합니다.
각 기능 안에서 요청을 받는 Controller, 업무 규칙을 처리하는 Service,
DB 접근을 담당하는 Repository, 테이블과 연결되는 Entity, 입출력 DTO를 필요에 따라 만듭니다.
예를 들어 STEP 2에서는 먼저 테이블을 설계한 뒤 `user`와 `ingredient` 등의 Entity를 작성합니다.

### 프론트엔드의 화면 흐름

```text
index.html의 #root 요소
    → src/main.jsx
    → App.jsx
    → 시작 화면 컴포넌트와 CSS
```

`index.html`은 React가 표시될 HTML 요소를 준비합니다.
`main.jsx`의 `createRoot`가 그 요소에 React 컴포넌트를 렌더링합니다.
`App.jsx`는 최상위 컴포넌트이며 이후 페이지 구성을 연결할 위치입니다.
JSX는 JavaScript 안에서 화면 구조를 HTML과 비슷한 모양으로 표현하는 문법입니다.

Vite는 개발 서버와 빌드를 담당합니다.
`npm run dev`는 변경 사항을 즉시 확인하는 개발 서버를,
`npm run build`는 배포용 정적 파일을 만드는 명령을 실행합니다.
`npm run lint`는 ESLint로 JavaScript와 React 코드 규칙을 검사합니다.
`package-lock.json`과 `npm ci`를 함께 사용하면 저장소에 기록된 의존성 버전을 설치합니다.

## 5. 실행 방법

프로젝트 루트에서 다음 명령으로 환경을 확인합니다.

```powershell
java -version
javac -version
node --version
npm --version
```

JDK 21과 Node.js 24 LTS를 권장합니다.
`JAVA_HOME`을 설정한 환경에서는 해당 경로가 JDK를 가리키는지도 확인하세요.

**터미널 1: 백엔드 실행**

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Maven Wrapper가 필요한 Maven 버전과 라이브러리를 처음 한 번 내려받습니다.
로그에서 `Started FridgeGatekeeperApplication`을 확인한 뒤 브라우저에서
[상태 확인 API](http://localhost:8080/api/health)를 열어 `UP` 응답을 확인합니다.
백엔드의 `/`에는 페이지를 만들지 않았으므로 반드시 `/api/health`로 접속합니다.

**터미널 2: 프론트엔드 실행**

```powershell
cd frontend
npm ci
npm run dev
```

[React 시작 화면](http://localhost:5173)을 엽니다.
모바일에서는 개발자 도구의 기기 모드를 사용하여 좁은 화면의 배치를 확인할 수 있습니다.
개발 서버는 로컬 PC에서 확인하도록 설정되어 있습니다.

두 서버는 각각 실행되므로 한쪽을 종료해도 다른 쪽은 계속 실행됩니다.
종료할 때는 각 터미널에서 `Ctrl+C`를 누릅니다.

**빌드 및 기본 검사**

```powershell
# 새 터미널을 프로젝트 루트에서 엽니다.
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress verify
cd ..\frontend
npm ci
npm run lint
npm run build
```

Maven의 `verify`는 컴파일, 기본 테스트, JAR 패키징을 수행합니다.
기본 테스트는 Spring 애플리케이션이 시작되는지 검사합니다.
회원·식재료·추천 기능의 테스트는 해당 기능을 구현하면서 추가합니다.

빌드된 백엔드 JAR는 `backend`에서 다음처럼 실행할 수 있습니다.

```powershell
java -jar target/fridge-gatekeeper-0.0.1-SNAPSHOT.jar
```

### 실행 중 자주 만나는 문제

| 증상 | 확인 및 해결 |
| --- | --- |
| `JAVA_HOME` 관련 오류 | `JAVA_HOME`이 `bin` 폴더의 상위인 JDK 설치 폴더를 가리키는지 확인 |
| `release version 21 not supported` | JDK 21을 설치하고 `java`, `javac`, `JAVA_HOME`이 사용할 JDK를 확인 |
| PowerShell에서 `npm.ps1` 실행이 차단됨 | 보안 정책을 바꾸지 않고 `npm.cmd ci`, `npm.cmd run dev` 사용 |
| 8080 포트 사용 중 | 기존 서버를 종료하거나 `$env:SERVER_PORT = '8081'` 설정 후 백엔드 실행, URL도 8081로 변경 |
| 5173 포트 사용 중 | 기존 Vite 서버를 종료하거나 `npm run dev -- --port 5174`로 실행 |
| Maven/npm 다운로드 실패 | 인터넷 연결 및 프록시 설정 확인 후 같은 명령 재실행 |

### 이번 단계의 실행 확인 결과

- Maven `verify`: 성공. 기본 테스트 1개 통과, 실행 가능한 JAR 생성.
- JAR 실행 후 `GET /api/health`: HTTP 200, `{"status":"UP","service":"fridge-gatekeeper"}` 확인.
- 프론트엔드 `npm run lint`: 통과.
- 프론트엔드 `npm run build`: 성공, `dist` 생성.
- Vite 개발 서버: HTTP 200 및 React 진입점 확인.

검증용 서버는 확인 후 종료했습니다. 화면을 확인할 때 위 명령으로 다시 실행하세요.
반응형 CSS는 작성했으며, 실제 모바일 브라우저 렌더링 검사는 아직 수행하지 않았습니다.

## 다음 단계

STEP 2에서 네 개 테이블의 관계, 컬럼 타입, 외래 키, 제약 조건을 문서로 먼저 설계합니다.
그 뒤 MySQL 설정과 Spring Data JPA 의존성을 추가하고 Entity를 작성합니다.
회원 기능은 STEP 3에서 진행합니다.
