# CI / 정적 분석 가이드

GitHub Actions로 돌아가는 테스트, 정적 분석, 의존성 관리 도구를 정리한 문서다.
각 도구가 무엇을 하는지, 어떻게 설정했는지, 결과를 어디서 보는지 설명한다.

| 도구 | 역할 | 설정 파일 | 실행 시점 | 결과 위치 |
|---|---|---|---|---|
| CI (테스트) | 단위/통합 테스트 실행, 실패 시 배포 차단 | `.github/workflows/ci.yml` | push(dev, main), 모든 PR | Actions 탭 → CI |
| SpotBugs | 버그 패턴 정적 분석 (바이트코드) | `build.gradle`, `config/spotbugs/exclude.xml` | CI 안에서 함께 실행 | Actions 탭 → CI 로그, 실패 시 보고서 artifact |
| CodeQL | 보안 취약점 정적 분석 (소스) | `.github/workflows/codeql.yml` | push(dev, main), 모든 PR, 매주 월 09:00 KST | Security 탭 → Code scanning |
| Dependabot | 의존성/Action 버전 업데이트 PR 생성 | `.github/dependabot.yml` | 매주 월 09:00 KST | Pull requests 탭 |
| 배포 | Docker 이미지 빌드 + 맥미니 배포 | `.github/workflows/deploy.yml` | CI 통과 후 자동(dev push), 수동 실행 | Actions 탭 → CI의 deploy job / Deploy_dev |

## 전체 흐름

```
dev push ──▶ CI ─┬─ test: 단위 테스트 → 통합 테스트 → SpotBugs
                 │        ├─ 통과 ─▶ deploy job (deploy.yml 호출) ─▶ 맥미니 배포
                 │        └─ 실패 ─▶ 배포 안 함, 테스트/SpotBugs 보고서 업로드
         └─▶ CodeQL (CI와 별도로 병렬 실행, 배포와 무관)

main push / PR ──▶ CI(test만, 배포 없음) + CodeQL
매주 월 09:00   ──▶ CodeQL 재분석, Dependabot 버전 확인
```

- 배포는 **dev push에서 CI가 통과한 경우에만** 실행된다. PR과 main push에서는 배포하지 않는다.
- CodeQL 결과는 배포를 막지 않는다. 경고는 Security 탭에서 따로 확인하고 처리한다.

---

## 1. CI — 테스트 (`ci.yml`)

### 특징
- GitHub이 제공하는 Ubuntu 러너에서 프로젝트를 새로 받아 테스트를 실행한다. "내 PC에서는 됐는데"를 막는다.
- 통합 테스트용 PostgreSQL 16을 [service container](https://docs.github.com/actions/using-containerized-services/about-service-containers)로 띄운다. 별도 DB 서버가 필요 없다.
- 테스트가 실패하면 뒤의 배포 job이 실행되지 않는다.

### 설정
| 항목 | 값 | 이유 |
|---|---|---|
| 트리거 | `push`(dev, main), `pull_request` | |
| 실행 명령 | `./gradlew test integrationTest spotbugsMain --continue` | `--continue`: 하나가 실패해도 나머지를 끝까지 실행해 실패를 한 번에 모두 본다 |
| DB | `postgres:16`, DB `note_test` / `testuser` / `testpass`, 포트 `55433` | `application-test.yml`의 접속 정보와 같아야 한다 |
| JDK | Temurin 21 | |
| Gradle 캐시 | `gradle/actions/setup-gradle@v4` | 의존성 캐시로 빌드 시간 단축 |
| 타임존 | `TZ=Asia/Seoul` | 로그의 `LocalDateTime`을 로컬 개발 환경과 같은 기준으로 |
| 동시 실행 | 같은 브랜치의 PR은 새 커밋이 오면 이전 실행 취소, push는 취소하지 않음 | push는 배포와 연결돼 있어 중간에 끊지 않는다 |
| 제한 시간 | 20분 | |

### 결과 확인
- **Actions 탭 → CI → 해당 실행**. job `test`, `deploy / deploy` 순서로 표시된다.
- PR에서는 PR 하단 Checks 영역에 결과가 표시된다.
- 실패 시: 실행 화면 하단 **Artifacts → `reports`**를 내려받는다(7일 보관).
  - `build/reports/tests/test/index.html` — 단위 테스트
  - `build/reports/tests/integrationTest/index.html` — 통합 테스트
  - `build/reports/spotbugs/main.html` — SpotBugs

### 로컬에서 같은 검사 실행
```bash
./gradlew test integrationTest spotbugsMain --continue
```
통합 테스트는 로컬 PostgreSQL(`localhost:55433/note_test`)이 떠 있어야 한다.

---

## 2. SpotBugs

### 특징
- 컴파일된 **바이트코드**를 분석해 버그가 되기 쉬운 패턴을 찾는다. 예: null 역참조 가능성, `equals`/`hashCode` 불일치, 닫히지 않은 리소스, 잘못된 동기화.
- 테스트로는 잡기 어려운 "아직 터지지 않은" 문제를 찾는 용도다. 보안 취약점은 CodeQL이 담당한다.
- **경고가 1건이라도 있으면 빌드가 실패**한다. 경고를 쌓아두지 않기 위해서다.

### 설정
`build.gradle`:
```groovy
plugins {
    id 'com.github.spotbugs' version '6.0.26'
}

spotbugs {
    ignoreFailures = false                              // 경고가 있으면 빌드 실패
    excludeFilter = file('config/spotbugs/exclude.xml') // 오탐 제외 규칙
}

tasks.named('spotbugsMain') {
    reports {
        html.required = true    // HTML 보고서 생성
        xml.required = false
    }
}

tasks.named('spotbugsTest') {
    enabled = false             // 테스트 코드는 분석하지 않음
}
```

`config/spotbugs/exclude.xml`에서 제외한 항목 (근거는 파일 주석에도 있다):

| 제외 대상 | 이유 |
|---|---|
| QueryDSL `Q*` 클래스 | 자동 생성 코드 |
| `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` (가변 객체를 복사 없이 보관/반환) | 생성자 주입받은 Spring 빈, record DTO의 `List` 필드에서 대부분 발생. 빈은 공유가 의도이고, DTO는 직렬화 직전까지만 쓰여 방어적 복사의 이득이 없다 |
| `CT_CONSTRUCTOR_THROW` (생성자 예외 → finalizer 공격 가능성) | `JwtUtil` 등은 설정이 잘못되면 생성자에서 예외를 던져 기동을 실패시키는 것이 의도이고, `finalize`를 쓰지 않아 해당 공격이 성립하지 않는다 |

처음 적용 시 경고 12건 → 10건은 위 규칙으로 제외, 2건(`ChatService`의 `TransactionTemplate.execute` 반환값 null 가능성)은 `Objects.requireNonNull`로 코드 수정해 **현재 0건**이다.

### 결과 확인
- **Actions 탭 → CI → test job 로그**의 `spotbugsMain` 단계. 실패하면 경고 개수가 출력된다.
- 상세 내용은 실패 시 업로드되는 `reports` artifact의 `spotbugs/main.html`.
- 로컬: `./gradlew spotbugsMain` 후 `build/reports/spotbugs/main.html`.

### 경고가 나왔을 때
1. 실제 문제면 코드를 고친다 (우선).
2. 오탐이면 `exclude.xml`에 **가능한 좁은 범위**(클래스/메서드 단위)로 제외 규칙을 추가하고, 주석으로 이유를 남긴다.

---

## 3. CodeQL (`codeql.yml`)

### 특징
- GitHub이 만든 **보안 취약점** 분석 도구. 코드를 데이터베이스로 만들어 "사용자 입력이 검증 없이 SQL/경로/로그 등에 흘러가는가" 같은 데이터 흐름을 추적한다.
- 찾는 예: SQL 인젝션, 경로 조작, 로그 인젝션, 약한 암호화, 하드코딩된 자격 증명, SSRF.
- 공개 저장소에서는 무료다.
- SpotBugs와 달리 **배포를 막지 않는다**. 결과는 Security 탭에 경고로 쌓이고, PR에서는 그 PR이 새로 만든 경고만 표시된다.

### 설정
| 항목 | 값 | 이유 |
|---|---|---|
| 언어 | `java-kotlin` | |
| `build-mode` | `none` | Gradle 빌드 없이 소스만 분석. DB/빌드 환경이 필요 없어 빠르고 실패할 일이 적다 |
| 규칙 | `security-extended` | 기본 규칙보다 범위가 넓다 (오탐이 조금 늘 수 있음) |
| 트리거 | `push`(dev, main), `pull_request`, 매주 월 00:00 UTC(09:00 KST) | 코드 변경이 없어도 새로 추가된 규칙으로 주 1회 재분석 |
| 권한 | `security-events: write` | 결과를 Security 탭에 올리기 위해 필요 |

### 결과 확인
- **저장소 Security 탭 → Code scanning**. 경고별 위치, 데이터 흐름 경로, 수정 방법이 나온다.
- 실행 로그: **Actions 탭 → CodeQL**.
- PR: Checks 영역 + 코드 줄에 직접 주석으로 표시.
- 현재(2026-10-28 첫 실행 기준) 열린 경고 0건.

### 경고가 나왔을 때
- 실제 취약점이면 수정 후 push → 다음 분석에서 자동으로 닫힌다.
- 오탐이면 Security 탭의 경고에서 **Dismiss**(사유: False positive / Used in tests / Won't fix)를 선택한다.

---

## 4. Dependabot (`dependabot.yml`)

### 특징
- 의존성의 새 버전을 확인해 **버전 업데이트 PR을 자동으로 만든다**. PR 본문에 변경 내역(릴리스 노트)이 붙는다.
- 각 PR에서 CI가 돌기 때문에, 초록불이면 merge해도 테스트 기준으로는 안전하다는 뜻이다.
- 보안 취약점이 알려진 버전은 Security 탭의 **Dependabot alerts**로도 알려준다(저장소 Settings → Code security에서 alerts/security updates가 켜져 있어야 한다).

### 설정
| 대상 | 확인 주기 | 대상 브랜치 | 동시 PR 수 | 묶음 |
|---|---|---|---|---|
| `gradle` (build.gradle 의존성, 플러그인) | 매주 월 09:00 KST | `dev` | 최대 5 | **패치 버전은 PR 하나로 묶음**, 마이너/메이저는 개별 PR |
| `github-actions` (workflow의 `uses:` 버전) | 매주 월 09:00 KST | `dev` | 최대 5 | 전부 PR 하나로 묶음 |

- dev로 PR을 보내는 이유: dev에서 검증 → 배포 확인 → main으로 가는 기존 흐름을 따른다.

### 결과 확인
- **Pull requests 탭**: 작성자가 `dependabot[bot]`인 PR.
- 실행 기록: **Insights 탭 → Dependency graph → Dependabot**.
- 보안 알림: **Security 탭 → Dependabot alerts**.

### PR 처리 기준
- 패치 묶음 PR: CI 통과면 merge.
- 마이너/메이저(예: Spring Boot 3.2 → 3.x): CI 통과 여부 + 릴리스 노트의 breaking change 확인 후 merge. 불필요하면 PR에 `@dependabot ignore this major version` 등으로 댓글.
- merge하면 dev push가 되므로 **CI 통과 후 자동 배포**된다.

---

## 5. 배포 (`deploy.yml`)

배포 workflow는 CI에서 호출되는 형태(`workflow_call`)로 바뀌었다. 자세한 단계는 파일 주석 참고.

| 방법 | 언제 |
|---|---|
| 자동 | dev push → CI의 test job 통과 → `deploy` job이 `deploy.yml` 호출 |
| 수동 | **Actions 탭 → Deploy_dev → Run workflow → 브랜치 dev 선택**. 테스트 없이 다시 배포해야 할 때(서버 재기동, 설정 재반영 등) |

배포 후 확인 항목 (실패하면 job 실패):
1. 앱 헬스 체크 `http://localhost:8080/test` (5초 간격 최대 24회)
2. nginx 재시작 후 호스트의 `nginx.conf`와 컨테이너 안 설정 파일의 md5가 같은지 확인

결과: **Actions 탭 → CI → `deploy / deploy` job** (수동 실행은 Deploy_dev).

---

## 참고 / 남은 작업

- `actions/setup-java@v4`, Node 20 기반 action에 대한 deprecation 경고가 나온다. Dependabot의 github-actions PR로 올리면 된다.
- Tailscale `authkey` 방식은 OAuth 클라이언트 방식으로 바꾸라는 권고가 있다. 현재 동작에는 문제 없음.
- main 브랜치 보호 규칙(Settings → Branches)에서 **CI 통과를 merge 필수 조건**으로 걸어두면, 테스트가 깨진 코드가 main에 들어가는 것을 막을 수 있다 (아직 설정 안 함).
