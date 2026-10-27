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
    - 같은 채팅방에 AI 응답을 기다리는 전송이 있으면 409 `CHAT_IN_PROGRESS` (잠금은 Ollama 최대 대기시간 + 60초, 기본 약 185초 후 자동 만료).
    - AI 호출 또는 응답 저장이 실패하면 사용자 메시지도 저장되지 않는다 (503 `AI_SERVICE_UNAVAILABLE` 등).
    - Ollama 대기는 최대 connect + read timeout(기본 5 + 120초). nginx `proxy_read_timeout`(150초)은 이보다 커야 한다.
      그보다 짧으면 클라이언트는 504를 받는데 서버는 응답을 저장하게 된다.
    - 백엔드는 자체 호스팅 중인 Ollama 서버에 요청을 전달(proxy)한다.