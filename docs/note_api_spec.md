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