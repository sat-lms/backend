package com.sat.lms.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 애플리케이션 컨텍스트(springdoc 자동 설정 포함)에서 SecurityConfig의
 * permitAll/anyRequest().authenticated() 정책을 검증한다. @WebMvcTest 슬라이스에서는
 * springdoc이 등록하는 /v3/api-docs, /swagger-ui/** 리소스 핸들러가 로드되지 않으므로
 * 이 부분은 전체 컨텍스트가 필요하다.
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
class SecurityConfigPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lms_test")
            .withUsername("lms_test")
            .withPassword("lms_test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired MockMvc mockMvc;

    @Test
    void apiDocsIsPubliclyAccessible() throws Exception {
        // deploy.yml의 배포 후 헬스체크가 이 경로를 쓴다 — permitAll 목록에서 빠지면
        // 다음 배포부터 CD가 실패한다.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void unmappedPathWithoutAuthenticationIsRejectedInFullApplicationContext() throws Exception {
        mockMvc.perform(get("/api/v1/totally-made-up-path"))
                .andExpect(status().isUnauthorized());
    }
}
