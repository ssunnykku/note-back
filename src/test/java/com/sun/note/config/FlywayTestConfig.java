package com.sun.note.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 통합 테스트 DB를 매 기동마다 비우고 마이그레이션을 처음부터 적용한다.
 * 운영과 같은 스키마(db/migration)로 테스트하기 위함.
 */
@Configuration
@Profile("test")
public class FlywayTestConfig {

    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
