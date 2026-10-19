---
name: Feature Request
about: 새로운 기능을 제안합니다
title: "[FEATURE] 카테고리 CRUD 기능 구현"
labels: enhancement
assignees: ""
---

## 기능 설명

- 노트를 분류할 수 있는 카테고리 생성 및 조회 기능을 구현한다.
- 카테고리별 노트 목록을 조회할 수 있다.

## 문제점 또는 필요성

노트가 늘어날수록 분류 없이는 관리가 어렵다. 카테고리를 통해 노트를 체계적으로 관리할 수 있어야 한다.

## 제안하는 해결방법

### Category 엔티티
- `id` (Long) - PK
- `name` (String) - 카테고리명 (최대 100자)

### API 설계

| 동작 | Method | Endpoint |
|---|---|---|
| 카테고리 생성 | `POST` | `/api/categories` |
| 카테고리별 노트 목록 조회 | `GET` | `/api/categories/{userId}/notes` |

### 구현 내용
- **카테고리 생성**: name 필수, 최대 100자 validation
- **카테고리별 노트 목록 조회**: QueryDSL을 사용하여 카테고리-노트 left join 조회, userId 기준 필터링, `deleted` query parameter로 휴지통 조회 지원 예정

## 대안

- 태그 기반 분류: 다대다 관계로 복잡도가 높아 우선 카테고리(1:N) 방식 채택

## 추가 컨텍스트

- Note 엔티티의 `categoryId` 필드와 연관 (nullable — 미분류 노트 지원)
- 카테고리 수정/삭제는 별도 이슈로 분리

## 우선순위

- [x] High (높음)
- [ ] Medium (보통)
- [ ] Low (낮음)
