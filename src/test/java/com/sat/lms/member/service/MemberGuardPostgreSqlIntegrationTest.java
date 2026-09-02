package com.sat.lms.member.service;

import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 시점 이후, 매 요청마다 회원 상태(MemberStatus)가 재검증되는지 확인한다.
 * JWT 자체는 서명·만료가 유효한 채로 유지되지만, DB의 회원 상태가 로그인 이후
 * WITHDRAWN/REJECTED로 바뀌면 같은 토큰으로도 더 이상 접근할 수 없어야 한다(#51).
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
class MemberGuardPostgreSqlIntegrationTest {
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

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean FileStorage fileStorage;

    @BeforeEach
    void cleanData() {
        jdbcTemplate.update("DELETE FROM submission_attachment");
        jdbcTemplate.update("DELETE FROM submission");
        jdbcTemplate.update("DELETE FROM assignment_attachment");
        jdbcTemplate.update("DELETE FROM notice_attachment");
        jdbcTemplate.update("DELETE FROM attachment");
        jdbcTemplate.update("DELETE FROM assignment");
        jdbcTemplate.update("DELETE FROM notice_read");
        jdbcTemplate.update("DELETE FROM notice");
        jdbcTemplate.update("DELETE FROM member_review");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    void approvedMemberPassesGuardedEndpointsAcrossServices() throws Exception {
        Long adminId = insertMember("admin01", "관리자", "ADMIN", "APPROVED");
        Long studentId = insertMember("student01", "학생", "STUDENT", "APPROVED");
        Long noticeId = insertNotice(adminId, "공지");
        Long assignmentId = insertAssignment(adminId);
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출 내용"))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawnMemberIsRejectedByGuardedEndpointsEvenWithStillValidToken() throws Exception {
        Long adminId = insertMember("admin02", "관리자", "ADMIN", "APPROVED");
        Long studentId = insertMember("student02", "학생", "STUDENT", "APPROVED");
        Long noticeId = insertNotice(adminId, "공지");
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        // 로그인(토큰 발급) 시점에는 승인 상태이므로 정상 접근된다.
        mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // 토큰은 그대로 두고, 그 사이 회원이 탈퇴 처리됐다고 가정한다.
        jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", studentId);

        mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("탈퇴하거나 정지된 계정입니다."));
        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("탈퇴하거나 정지된 계정입니다."));
    }

    @Test
    void rejectedMemberIsRejectedBySubmissionEndpointEvenWithStillValidToken() throws Exception {
        Long adminId = insertMember("admin03", "관리자", "ADMIN", "APPROVED");
        Long studentId = insertMember("student03", "학생", "STUDENT", "APPROVED");
        Long assignmentId = insertAssignment(adminId);
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        jdbcTemplate.update("UPDATE member SET status = 'REJECTED' WHERE id = ?", studentId);

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출 내용"))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("탈퇴하거나 정지된 계정입니다."));
        assertNoSubmissionWasCreated(assignmentId, studentId);
    }

    @Test
    void demotedAdminLosesAdminEndpointAccessEvenWithStillValidToken() throws Exception {
        Long adminId = insertMember("admin04", "관리자", "ADMIN", "APPROVED");
        Long assignmentId = insertAssignment(adminId);
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 관리자 계정이라도 탈퇴 처리되면, role 검사(ADMIN)는 여전히 통과하지만
        // 상태 검사에서 막혀야 한다 — 이게 이번 작업이 막는 취약점의 핵심 시나리오다.
        jdbcTemplate.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", adminId);

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("탈퇴하거나 정지된 계정입니다."));
    }

    private void assertNoSubmissionWasCreated(Long assignmentId, Long studentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission WHERE assignment_id = ? AND student_id = ?",
                Integer.class, assignmentId, studentId);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }

    private MockMultipartFile jsonPart(String textContent) {
        String body = "{\"textContent\":\"" + textContent + "\"}";
        return new MockMultipartFile("request", "request", "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private Long insertMember(String studentNumber, String name, String role, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, ?, now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role, status);
    }

    private Long insertNotice(Long adminId, String title) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notice (admin_id, title, content, is_pinned, created_at, updated_at)
                VALUES (?, ?, '내용', false, now(), now()) RETURNING id
                """, Long.class, adminId, title);
    }

    private Long insertAssignment(Long adminId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment (admin_id, title, content, due_at, allow_late_submission,
                                        created_at, updated_at)
                VALUES (?, '과제', '내용', ?, true, now(), now()) RETURNING id
                """, Long.class, adminId,
                OffsetDateTime.now().plusDays(1).withOffsetSameInstant(ZoneOffset.UTC));
    }
}
