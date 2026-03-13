# Note Backend

Spring Boot 기반 노트 애플리케이션 백엔드

## 기술 스택

- Java 21
- Spring Boot 3.2.3
- Spring Data JPA
- QueryDSL
- PostgreSQL 16

## Docker로 실행

### 실행

```bash
docker compose up --build -d
```

### 중지

```bash
docker compose down
```

### 소스 수정 후 반영

로컬 소스가 컨테이너에 볼륨 마운트되어 있으므로, 재시작만 하면 변경된 코드가 반영됩니다.

```bash
docker compose restart app
```

### 로그 확인

```bash
docker compose logs -f app
```

### DB 접속

```bash
docker exec -it postgres-db psql -U postgres -d note
```

### 접속 정보

| 항목 | 내용                                    |
| ---- | --------------------------------------- |
| 앱   | `http://localhost:8080`                 |
| DB   | `localhost:55432` (postgres / postgres) |
