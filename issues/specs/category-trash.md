---
name: Feature Request
about: 새로운 기능을 제안합니다
title: "[FEATURE] 카테고리 삭제 기능 구현"
labels: enhancement
assignees: ""
---

## 기능 설명

- 카테고리 삭제 시 카테고리는 즉시 삭제(hard delete)한다.
- 삭제된 카테고리에 속한 하위 노트들은 미분류 상태(`categoryId = null`)로 변경된다.

## 문제점 또는 필요성

카테고리 정리 시 하위 노트까지 삭제되면 데이터 손실이 발생한다. 카테고리만 삭제하고 노트는 미분류로 유지하여 데이터를 보존해야 한다.

## 제안하는 해결방법

### Note 엔티티 변경
- `categoryId` 필드를 `@NotNull` → nullable로 변경
- `categoryId = null`인 노트는 "미분류" 노트로 취급

### API 설계

| 동작 | Method | Endpoint |
|---|---|---|
| 카테고리 삭제 | `DELETE` | `/api/categories/{id}` |

### 삭제 시 동작
1. 해당 카테고리에 속한 노트들의 `categoryId`를 `null`로 업데이트
2. 카테고리를 DB에서 즉시 삭제

### 기존 API 변경사항
- 카테고리별 노트 목록 조회 시 미분류(`categoryId = null`) 노트 그룹을 별도로 표시
- QueryDSL 쿼리에서 `categoryId IS NULL` 노트를 "미분류" 그룹으로 처리

## 대안

- 카테고리도 soft delete 후 영구삭제: 카테고리까지 휴지통을 적용하면 구조가 복잡해져, 카테고리는 즉시 삭제 + 노트 미분류 전환 방식 채택

## 추가 컨텍스트

- note-trash 이슈와 연관: 미분류 노트도 개별적으로 휴지통 이동/복원 가능
- 미분류 노트에 대해 카테고리 재지정 기능 필요 (기존 노트 수정 API로 처리 가능)

## 우선순위

- [ ] High (높음)
- [x] Medium (보통)
- [ ] Low (낮음)
