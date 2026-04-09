-- ============================================================
-- Note — DDL (PostgreSQL)
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

    )

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
        deleted_at TIMESTAMP

    )

-- 카테고리
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY, 
    user_id UUID NOT NULL, 
    name VARCHAR(100) NULL

    )
    
-- 인덱스
CREATE INDEX idx_user_notes ON notes (user_id);
CREATE INDEX idx_category_notes ON notes (category_id);
CREATE INDEX idx_user_categories ON categories (user_id);