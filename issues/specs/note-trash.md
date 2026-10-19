---
name: Feature Request
about: 새로운 기능을 제안합니다
title: "[FEATURE] 노트 휴지통/완전삭제 기능 구현"
labels: enhancement
assignees: ""
---

## 기능 설명

- 노트 삭제 시 즉시 삭제 대신 휴지통(soft delete)으로 이동하는 기능을 구현한다.
- 휴지통에서 복원 또는 완전 삭제(hard delete)를 선택할 수 있다.

## 문제점 또는 필요성

현재 삭제 API(`DELETE /api/notes/{id}`)는 DB에서 즉시 삭제되어 복구가 불가능하다. 사용자의 실수로 인한 데이터 손실을 방지할 수 있어야 한다.

## 제안하는 해결방법

### Note 엔티티 변경
- `deleted` (Boolean) - soft delete 여부
- `deletedAt` (LocalDateTime) - 삭제 시점

### API 설계

| 동작 | Method | Endpoint |
|---|---|---|
| 휴지통으로 이동 (soft delete) | `DELETE` | `/api/notes/{id}` |
| 완전 삭제 (hard delete) | `DELETE` | `/api/notes/{id}/permanent` |
| 휴지통에서 복원 | `PATCH` | `/api/notes/{id}/restore` |
### 기존 API 변경사항
- 기존 `DELETE /api/notes/{id}`를 soft delete로 변경 (deleted=true, deletedAt 기록)
- 기존 카테고리별 노트 목록 조회 API에 query parameter 추가로 휴지통 조회 지원
  - `GET /api/categories/{userId}/notes?deleted=false` — 기본 노트 목록 (default)
  - `GET /api/categories/{userId}/notes?deleted=true` — 휴지통 목록
- 휴지통 목록 조회 시 미분류(`categoryId = null`) 노트도 "미분류" 그룹으로 표시

## 대안

- 기존 DELETE API에 query parameter flag로 삭제 유형 구분: 의미가 모호해지고 REST 시맨틱에 맞지 않아 엔드포인트 분리 방식 채택

## 추가 컨텍스트

- 휴지통 보관 기간 정책 (예: 30일 후 자동 완전삭제) 추후 검토
- NoteRepository에 `deleted` 필드 기반 조회 쿼리 추가 필요

## 우선순위

- [ ] High (높음)
- [x] Medium (보통)
- [ ] Low (낮음)
