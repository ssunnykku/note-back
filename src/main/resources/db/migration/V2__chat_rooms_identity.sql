-- chat_rooms.id: 프론트 발급(Date.now()) → 서버 발급(시퀀스)으로 전환
-- 엔티티 ChatRoom.id 가 @GeneratedValue(IDENTITY)로 바뀌었으므로 컬럼에 시퀀스 기본값이 필요하다.
-- (ddl-auto: validate 는 컬럼 기본값까지 검사하지 않으므로 누락돼도 기동은 되고 INSERT 시점에 실패한다)
--
-- 기존 행의 id(Date.now() 값, 약 1.7e12)는 그대로 두고, 시퀀스는 기존 최댓값 다음부터 발급한다.
-- 단, JS에서 정확히 표현할 수 없는 2^53 이상 id(클라이언트가 임의로 보낸 값)는 시작값 계산에서 제외한다.
-- (포함하면 Long 최댓값 같은 행 때문에 시퀀스가 즉시 overflow 된다)
-- 트랜잭션은 Flyway가 감싼다.

CREATE SEQUENCE IF NOT EXISTS chat_rooms_id_seq OWNED BY chat_rooms.id;

SELECT setval(
        'chat_rooms_id_seq',
        COALESCE((SELECT MAX(id) FROM chat_rooms WHERE id < 9007199254740992), 0) + 1,
        false);

ALTER TABLE chat_rooms ALTER COLUMN id SET DEFAULT nextval('chat_rooms_id_seq');
