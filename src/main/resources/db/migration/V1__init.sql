-- ============================================================
-- Note — 초기 스키마 (Flyway baseline)
-- ============================================================

-- Note 및 카테고리 관리에 대한 DDL
-- FK 제약은 걸지 않음 (DELETE CASCADE 역시 코드 레벨에서 제어한다.)

-- 사용자
CREATE TABLE users (
        id UUID PRIMARY KEY,
        name VARCHAR(50) NULL,
        email VARCHAR(255) NOT NULL UNIQUE,
        password VARCHAR(64) NOT NULL,
        created_at TIMESTAMP NOT NULL,
        updated_at TIMESTAMP

    );

-- 노트
CREATE TABLE notes (
        id BIGSERIAL PRIMARY KEY,
        user_id UUID NOT NULL,
        category_id BIGINT,
        title VARCHAR(255) NOT NULL,
        content TEXT NOT NULL,
        deleted BOOLEAN NOT NULL DEFAULT 'false',
        created_at TIMESTAMP NOT NULL,
        updated_at TIMESTAMP,
        deleted_at TIMESTAMP,
        version BIGINT

    );

-- 카테고리
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY, 
    user_id UUID NOT NULL, 
    name VARCHAR(100) NULL

    );
    
-- AI 채팅방 (id는 프론트에서 생성해 전달하는 값을 그대로 사용, 첫 메시지 전송 시 upsert)
CREATE TABLE chat_rooms (
    id BIGINT PRIMARY KEY,
    user_id UUID NOT NULL,
    model_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL

    );

-- AI 채팅 메시지
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    chat_room_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL

    );

-- 인덱스
CREATE INDEX idx_user_notes ON notes (user_id);
CREATE INDEX idx_category_notes ON notes (category_id);
CREATE INDEX idx_user_categories ON categories (user_id);
CREATE INDEX idx_user_chat_rooms ON chat_rooms (user_id);
CREATE INDEX idx_chat_room_messages ON chat_messages (chat_room_id);