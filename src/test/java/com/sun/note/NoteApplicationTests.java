package com.sun.note;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 실제 DB가 필요하므로 단위 테스트(test)가 아닌 통합 테스트(integrationTest)로 실행한다.
// (기본 프로필 dev는 DB_URL 환경변수가 없으면 기동하지 못해 test 태스크가 항상 실패하고, 그 뒤 integrationTest가 실행되지 않았다)
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class NoteApplicationTests {

	@Test
	void contextLoads() {
	}

}
