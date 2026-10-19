---
name: Feature Request
about: 새로운 기능을 제안합니다
title: "[FEATURE] JWT 기반 사용자 인증 시스템 구현"
labels: enhancement
assignees: ""
---

## 기능 설명

- Spring Security + JWT를 활용한 사용자 인증/인가 시스템을 구현한다.
- 회원가입, 로그인, 토큰 발급/갱신 기능을 포함한다.

## 문제점 또는 필요성

현재 인증 없이 모든 API가 공개되어 있어, 사용자별 노트 관리 및 접근 제어가 불가능하다.

## 제안하는 해결방법

- **User 엔티티 및 회원가입 API** (`POST /api/auth/signup`)
- **로그인 API** (`POST /api/auth/login`) - Access Token + Refresh Token 발급
- **Spring Security 설정** - JWT 인증 필터 적용
- **토큰 갱신 API** (`POST /api/auth/reissue`) - Refresh Token으로 Access Token 재발급
- **비밀번호 암호화** - BCryptPasswordEncoder 사용

## 대안

- 세션 기반 인증: stateful하여 확장성이 떨어지므로 JWT 방식 채택

## 추가 컨텍스트

- Spring Security 6.x 기반
- Access Token 만료 시간: 30분, Refresh Token: 7일
- 기존 노트 API에 인증 적용 필요

## 우선순위

- [x] High (높음)
- [ ] Medium (보통)
- [ ] Low (낮음)
