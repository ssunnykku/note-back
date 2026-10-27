-- chat_rooms_id_seq 재정렬
-- V2 적용 후에도 이전 버전 앱(프론트가 Date.now()로 id를 발급)이 실행 중인 동안에는 시퀀스를 거치지 않은 id가 들어온다.
-- 서버 발급 버전이 배포되는 이 시점에 시퀀스를 다시 기존 최댓값 다음으로 맞춰 PK 충돌을 막는다.
-- (V2와 같이 2^53 이상 id는 제외. 시퀀스가 이미 더 앞서 있으면 그대로 둔다)

SELECT setval(
        'chat_rooms_id_seq',
        GREATEST(
                (SELECT last_value FROM chat_rooms_id_seq),
                COALESCE((SELECT MAX(id) FROM chat_rooms WHERE id < 9007199254740992), 0)) + 1,
        false);
