## User
- 공통 인증 에러 (모든 API): 인증이 필요한 요청에 유효한 액세스 토큰이 없으면 **401** ProblemDetail
  (2026-10-28 변경, 기존 403 본문 없음). 경로가 없어도 인증이 먼저 검사된다.
  - `title: TOKEN_EXPIRED`: 서명은 올바르지만 만료된 액세스 토큰 → `POST /api/auth/refresh` 후 원래 요청 재시도
  - `title: UNAUTHORIZED`: 토큰 없음/위조/형식 오류 → 로그인 화면
- refresh 토큰 쿠키: `refresh_token`, HttpOnly, SameSite=Strict, Path=`/api/auth`, Max-Age 7일
  - local/dev는 HTTPS가 없어 Secure를 끈다(`auth.refresh-token.cookie-secure: false`). HTTPS 적용 시 켠다.
  - DB(`refresh_tokens`)에는 SHA-256 해시만 저장. 로그인(기기)마다 따로 발급된다.
- 로그인
`POST /api/auth/login` → 200 `{accessToken, user{name,email}}` + refresh 쿠키
- 회원가입
`POST /api/auth/signup` → 201 (로그인과 같은 응답) + refresh 쿠키
- 토큰 갱신
`POST /api/auth/refresh` (본문 없음, 쿠키만) → 200 `{accessToken, user{name,email}}` + **새** refresh 쿠키
  - 쓸 때마다 새 토큰으로 바뀐다(회전). 마지막 갱신 후 7일 동안 쓰지 않으면 만료.
  - 쿠키 없음/만료/이미 사용·로그아웃된 토큰 → 401 `INVALID_REFRESH_TOKEN`. **쿠키는 지우지 않는다**
    (쿠키 저장소는 탭끼리 공유돼, 늦게 도착한 실패 응답이 다른 탭이 막 받은 새 쿠키를 지우면 모든 탭이 로그아웃되기 때문. 쿠키 삭제는 logout만)
  - 쿠키 값은 디코딩하지 않고 그대로 비교한다(잘못된 % 인코딩도 401)
  - 같은 토큰으로 동시에 갱신하면 한 요청만 성공하고 나머지는 401
  - 한계: 재사용 탐지/절대 만료 없음. 7일 안에 계속 갱신하면 세션이 무기한 유지된다.
- 세션: 서버는 HTTP 세션을 만들지 않는다(STATELESS, JSESSIONID 없음). subject가 UUID가 아닌 액세스 토큰은 401 UNAUTHORIZED.
- 로그아웃
`POST /api/auth/logout` → 204 + 쿠키 삭제. refresh 토큰을 폐기한다(쿠키가 없어도 204).
  액세스 토큰은 서버에 상태가 없어 만료(최대 1시간)까지 유효하므로 프론트에서 지운다.

## Category
- 카테고리 생성(복합키 userId, categoryId)
    `POST /api/categories`
- 카테고리 수정
    `PATCH /api/categories/{id}`
- 카테고리별 노트 목록 조회 (categoryId 파라미터가 없으면 미분류 노트 반환)
    `GET /api/categories/notes?deleted=false`
    `GET /api/categories/notes`
- 노트 리스트 조회 by categoryId
    `GET /api/categories/`
    - categoryId = null인 노트는 "미분류" 노트로 취급한다.

## Note
- 노트 생성 (categoryId 생략 시 미분류 저장)
    `POST /api/notes`
- 노트 수정 (categoryId를 null로 수정하면 미분류 전환)
    `PUT /api/notes/{id}` 
- 조회
- 휴지통으로 이동 (soft delete)
    `DELETE /api/notes/{id}` (deleted=true, deletedAt 기록)
- 복원
    `PATCH /api/notes/{id}/restore`
- 완전 삭제
    `DELETE /api/notes/{id}/permanent`

## Chat (AI)
- 공통 에러: 인증 없음/만료 401(위 User 공통 인증 에러, 경로가 없어도 401), 매핑되지 않은 경로 404, 지원하지 않는 메서드 405,
  경로 변수 타입 오류·검증 실패 400, Content-Type 누락 415. 모두 ProblemDetail 형식.
- `createdAt`은 소수점 이하 초 자릿수가 응답마다 다르다. 생성/전송 응답은 서버 시각 그대로(Windows 7자리, Linux 최대 9자리),
  조회 응답은 DB에 저장된 값(마이크로초 6자리, 반올림)이라 같은 메시지라도 문자열이 다를 수 있다. 동일성 비교는 id로 한다.
- 채팅방 생성 (id는 서버 발급)
    `POST /api/chats`
    - 요청: `{ modelId? }` (body 생략 가능, modelId 최대 100자, 앞뒤 공백은 제거, 생략/공백뿐이면 null)
    - 응답 201: `{ id, title: null, modelId, lastMessage: null, createdAt }`
- 채팅 목록 조회 (본인 채팅방만, 생성일 최신순)
    `GET /api/chats`
    - 응답: `[{ id, title, modelId, lastMessage, createdAt }]`
    - title: 첫 사용자 메시지 (최대 50자), lastMessage: 가장 최근 메시지 (최대 100자)
    - 메시지가 없는 채팅방은 title, lastMessage가 null
- 메시지 조회 (오래된 순)
    `GET /api/chats/{chatId}/messages`
    - 응답: `[{ id, role(USER|AI), content, createdAt }]`
    - 없는 채팅방이거나 다른 사용자의 채팅방이면 404
- 메시지 전송
    `POST /api/chats/{chatId}/messages`
    - 채팅방은 `POST /api/chats`로 먼저 생성해야 한다. 없는 채팅방이거나 다른 사용자의 채팅방이면 404.
    - modelId(앞뒤 공백 제거)는 그대로 Ollama 모델명으로 쓰이고, 전송이 성공하면 채팅방의 현재 모델로 갱신된다 (대화 중 모델 변경 허용).
      예외: 처리가 잠금 만료(약 185초)보다 오래 걸려 그 사이 다른 전송이 잠금을 가져간 경우, 응답은 저장하지만 모델은 갱신하지 않는다.
      (timeout 설정상 정상적으로는 일어나지 않는 경우로, 이때는 대화 순서가 섞일 수 있다)
    - 사용자당 동시 전송은 1건: 본인의 어느 채팅방이든 AI 응답을 기다리는 전송이 있으면 409 `CHAT_IN_PROGRESS`
      (잠금은 Ollama 최대 대기시간 + 60초, 기본 약 185초 후 자동 만료).
    - 전송 횟수 제한: 사용자별로 최근 2분 동안 10회까지 (`chat.rate-limit`). 넘으면 429 `CHAT_RATE_LIMITED`.
      실패한 전송(503 등)은 횟수에 포함하지 않는다.
    - AI에는 이전 대화 중 최근 20개, 글자 수 합계 12,000자(새 메시지 포함) 안의 메시지만 함께 보낸다 (`chat.history`).
      범위 맨 앞의 AI 답변은 제외한다. 저장/조회되는 대화에는 영향이 없다.
    - AI 호출 또는 응답 저장이 실패하면 사용자 메시지도 저장되지 않는다 (503 `AI_SERVICE_UNAVAILABLE` 등).
    - Ollama 대기는 최대 connect + read timeout(기본 5 + 120초). 전송 한 건의 최악 처리 시간은 DB 커넥션 대기 30초씩 두 번을 더한 185초이고,
      nginx `proxy_read_timeout`(200초)은 이보다 커야 한다.
      그보다 짧으면 클라이언트는 504를 받는데 서버는 응답을 저장하게 된다.
    - 백엔드는 자체 호스팅 중인 Ollama 서버에 요청을 전달(proxy)한다.