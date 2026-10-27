## User
- 로그인
`POST /api/auth/login`
- 회원가입
`POST /api/auth/signup`
- 로그아웃
- 토큰 갱신
`POST /api/auth/reissue`

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
    `DELETE /api/notes/{id}/restore`

## Chat (AI)
- 채팅방 생성 (id는 서버 발급)
    `POST /api/chats`
    - 요청: `{ modelId? }` (body 생략 가능, modelId 최대 100자, 생략/빈 문자열이면 null)
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
    - modelId는 전송이 성공하면 채팅방의 현재 모델로 갱신된다 (대화 중 모델 변경 허용).
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