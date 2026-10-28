# CLAUDE.md

## 프로젝트 개요

노트/메모 작성 서비스의 백엔드. 프론트엔드는 별도 레포(`../note-front`)에서 관리한다.

- Java 21, Spring Boot 3.2.3, Spring Security + JWT
- Spring Data JPA + QueryDSL, PostgreSQL 16, Flyway
- AI 채팅: Ollama 연동 (`client/OllamaClient`)

```
src/main/java/com/sun/note/
├── controller/   # REST API (/api/**)
├── service/      # 비즈니스 로직
├── repository/   # JPA + QueryDSL (*QueryRepository, *QueryRepositoryImpl)
├── domain/entity/
├── dto/          # 요청/응답 record
├── exception/    # BusinessException, ErrorCode, GlobalExceptionHandler
├── client/       # Ollama 연동
├── filter/       # JwtFilter
├── config/       # Security, JWT, QueryDSL, Chat 설정
└── util/         # JwtUtil
```

## 실행

- 프로필: `local`(로컬 DB `localhost:55432`), `dev`(기본값, 서버 배포용, 환경변수 `DB_URL` `DB_USERNAME` `DB_PASSWORD` `JWT_SECRET` 필요), `test`(통합 테스트)
- Docker 실행: `docker compose -f docker/docker-compose.yml up --build -d`
  - `postgres-db`(55432), `postgres-test-db`(55433), `note-app`(8080)
- Swagger: `http://localhost:8080/swagger-ui/index.html`

## 규칙

### DB 마이그레이션 (Flyway)
- 스키마 변경은 `src/main/resources/db/migration/V{n}__설명.sql`을 **새로 추가**해서만 한다.
- 이미 존재하는 `V*.sql` 파일은 절대 수정하지 않는다. 체크섬이 맞지 않아 서버가 기동되지 않는다.
- `ddl-auto: validate`이므로 엔티티를 바꾸면 마이그레이션도 함께 추가해야 한다.

### 코드 컨벤션
- 비즈니스 예외는 `ErrorCode`에 항목을 추가하고 `BusinessException`으로 던진다. 응답 변환은 `GlobalExceptionHandler`가 담당한다.
- API를 추가하거나 바꾸면 `docs/note_api_spec.md`도 함께 갱신한다.
- 기능 스펙 문서는 `issues/specs/`에 있다. 스펙과 구현이 다르면 사용자에게 먼저 확인한다.

### CI / 정적 분석
- CI(`.github/workflows/ci.yml`)는 `./gradlew test integrationTest spotbugsMain --continue`를 실행하고, dev push에서 통과하면 배포한다.
- SpotBugs 경고가 하나라도 있으면 빌드가 실패한다. 오탐은 `config/spotbugs/exclude.xml`에 근거 주석과 함께 제외한다.

### Git
- 브랜치: `dev`에서 작업하고 `main`으로 PR.
- 커밋 메시지: `feat:` `fix:` `refactor:` `docs:` `ci:` `chore:` 접두어 + 한글 설명 (예: `fix: 회원가입시 validation 추가`)

## 테스트 (Kent Beck)

### 작업 방식
- 기능 추가와 버그 수정은 TDD로 한다. 설정, CI, 문서 변경은 해당하지 않는다.
  1. Red: 실패하는 테스트를 하나 작성하고 실패를 확인한다.
  2. Green: 통과시키는 최소한의 코드만 작성한다.
  3. Refactor: 테스트가 통과하는 상태를 유지하며 중복 제거와 구조 개선을 한다.
- 한 번에 테스트 하나씩, 작은 단계로 진행한다.
- 버그는 먼저 재현하는 실패 테스트를 작성한 뒤 고친다.
- **Tidy First**: 구조 변경(이름 변경, 메서드 추출 등 동작이 바뀌지 않는 변경)과 동작 변경을 한 커밋에 섞지 않는다. 구조 변경을 먼저 커밋한다.
- 모든 테스트가 통과할 때만 커밋한다.

### 작성 기준
- 구현 세부사항이 아니라 동작(입력 → 결과)을 검증한다. 리팩터링으로 깨지는 테스트를 만들지 않으며, 결과로 확인할 수 있는 것을 `verify`로 검증하지 않는다.
- 테스트 하나는 동작 하나만 검증한다.
- 현재 시각, 랜덤, 외부 네트워크(Ollama)에 의존하지 않는다. 테스트끼리 데이터를 공유하지 않는다.

### 기존 패턴
- 서비스 단위 테스트(`service/*Test`): Spring 컨텍스트 없이 `@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks`
- 컨트롤러 테스트(`controller/*Test`): `@WebMvcTest` + `MockMvc` + `@MockBean` 서비스 + `@WithMockUser(username = "<userId UUID>")`
- DTO 검증 테스트(`dto/*ValidationTest`): `jakarta.validation.Validator`를 직접 생성해 검증
- 공통
  - AssertJ(`assertThat`, `assertThatThrownBy`)
  - `@DisplayName("한글 설명")`, 본문은 `// given` `// when` `// then`
- 통합 테스트(`service/it/*IT`)
  - DB가 필요한 검증만 둔다 (트랜잭션, 락, 쿼리 등).
  - `@SpringBootTest` + `@ActiveProfiles("test")` + `@Tag("integration")`
  - `postgres-test-db`(55433)가 떠 있어야 한다. `FlywayTestConfig`가 기동 시 clean 후 migrate 한다.

### 실행
- 단위 테스트: `./gradlew test` (`@Tag("integration")` 제외)
- 통합 테스트: `./gradlew integrationTest`
- 특정 클래스: `./gradlew test --tests "com.sun.note.service.NoteServiceTest"`
