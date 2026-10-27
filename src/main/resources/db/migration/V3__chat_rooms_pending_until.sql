-- chat_rooms.pending_until: 메시지 전송(AI 응답 대기) 잠금 만료 시각
-- 같은 채팅방에 동시 전송을 막기 위해 추가. null이거나 현재 시각보다 이전이면 전송 가능.
-- (local DB는 예전 ddl-auto: update 로 이미 컬럼이 있을 수 있어 IF NOT EXISTS)

ALTER TABLE chat_rooms ADD COLUMN IF NOT EXISTS pending_until TIMESTAMP;
