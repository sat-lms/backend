package com.sat.lms.admin.controller;

import com.sat.lms.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
class AdminMemberPostgreSqlIntegrationTest {
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

    @BeforeEach
    void cleanData() {
        jdbcTemplate.update("DELETE FROM member_review");
        jdbcTemplate.update("DELETE FROM member");
    }

    @Test
    void listsAllMembersWithoutAnyFilter() throws Exception {
        Long adminId = insertMember("admin01", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        insertMember("student01", "학생1", "STUDENT", "APPROVED");
        insertMember("student02", "학생2", "STUDENT", "PENDING");

        mockMvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void filtersByRole() throws Exception {
        Long adminId = insertMember("admin02", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        insertMember("student03", "학생1", "STUDENT", "APPROVED");
        insertMember("student04", "학생2", "STUDENT", "APPROVED");

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("role", "STUDENT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("role", "ADMIN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void filtersByEachStatusValue() throws Exception {
        Long adminId = insertMember("admin03", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        insertMember("student05", "대기", "STUDENT", "PENDING");
        insertMember("student06", "승인", "STUDENT", "APPROVED");
        insertMember("student07", "거절", "STUDENT", "REJECTED");
        insertMember("student08", "탈퇴", "STUDENT", "WITHDRAWN");

        assertStatusCount(token, "PENDING", 1);
        assertStatusCount(token, "APPROVED", 2); // admin03 + student06
        assertStatusCount(token, "REJECTED", 1);
        assertStatusCount(token, "WITHDRAWN", 1);
    }

    @Test
    void withdrawnMembersAreNotHiddenFromDefaultListing() throws Exception {
        // 소프트 삭제 정책(#99, #103) — WITHDRAWN도 물리 삭제되지 않으므로 별도 필터 없는
        // 기본 조회에서 자연스럽게 포함되어야 한다.
        Long adminId = insertMember("admin04", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        insertMember("student09", "탈퇴학생", "STUDENT", "WITHDRAWN");

        mockMvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].status", org.hamcrest.Matchers.hasItem("WITHDRAWN")));
    }

    @Test
    void keywordMatchesStudentNumberOrName() throws Exception {
        Long adminId = insertMember("admin05", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        insertMember("20231234", "최인준", "STUDENT", "APPROVED");
        insertMember("20239999", "김철수", "STUDENT", "APPROVED");

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("keyword", "최인준")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("최인준"));

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("keyword", "20239999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].studentNumber").value("20239999"));
    }

    @Test
    void combinesRoleStatusAndKeywordFilters() throws Exception {
        Long adminId = insertMember("admin06", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        insertMember("20240001", "박지민", "STUDENT", "APPROVED");
        insertMember("20240002", "박지민", "STUDENT", "PENDING");
        insertMember("20240003", "박지민", "ADMIN", "APPROVED");

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("role", "STUDENT")
                        .param("status", "APPROVED")
                        .param("keyword", "박지민")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].studentNumber").value("20240001"));
    }

    @Test
    void paginationWorksAsExpected() throws Exception {
        Long adminId = insertMember("admin07", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        for (int i = 0; i < 5; i++) insertMember("stu" + i, "학생" + i, "STUDENT", "APPROVED");

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(3));
    }

    @Test
    void studentAndUnauthenticatedCannotListMembers() throws Exception {
        Long studentId = insertMember("student10", "학생", "STUDENT", "APPROVED");
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingMembersDoesNotTriggerNPlusOneQueries() throws Exception {
        Long adminId = insertMember("admin08", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        for (int i = 0; i < 10; i++) insertMember("stx" + i, "학생" + i, "STUDENT", "APPROVED");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(5));

        // 1) JwtAuthenticationFilter.validateToken()의 findTokenVersionById 조회(#106 토큰
        // 즉시 무효화 지원 이후 모든 인증 요청에 붙는 고정 비용), 2) 목록 constructor-expression
        // 조회, 3) count 쿼리 = 총 3건. 회원 수와 무관하게 고정.
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(3);
    }

    @Test
    void memberDetailWithSingleReviewIncludesReviewInfo() throws Exception {
        Long adminId = insertMember("admin09", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long reviewerId = insertMember("admin10", "심사자", "ADMIN", "APPROVED");
        Long targetId = insertMember("student11", "최인준", "STUDENT", "REJECTED");
        insertReview(targetId, reviewerId, "REJECTED", "서류 미비",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(targetId))
                .andExpect(jsonPath("$.data.name").value("최인준"))
                .andExpect(jsonPath("$.data.action").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("서류 미비"))
                .andExpect(jsonPath("$.data.reviewerId").value(reviewerId))
                .andExpect(jsonPath("$.data.reviewerName").value("심사자"));
    }

    @Test
    void memberDetailWithMultipleReviewsReturnsOnlyTheLatestOne() throws Exception {
        // 핵심 검증 케이스: PR #107(V11)로 한 회원이 여러 심사 기록을 가질 수 있게 됐다
        // (탈퇴 → 복구 신청 → 재승인). 오래된 기록이 섞여 반환되면 안 되고 가장 최근
        // 기록(reviewedAt 기준)만 응답에 나와야 한다.
        Long adminId = insertMember("admin11", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long firstReviewerId = insertMember("admin12", "최초심사자", "ADMIN", "APPROVED");
        Long secondReviewerId = insertMember("admin13", "재승인자", "ADMIN", "APPROVED");
        Long targetId = insertMember("student12", "김철수", "STUDENT", "APPROVED");
        // 시간 순서: 최초 승인(오래됨) → 자진 탈퇴 후 복구 신청 재승인(최신)
        insertReview(targetId, firstReviewerId, "APPROVED", null,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        insertReview(targetId, secondReviewerId, "APPROVED", null,
                OffsetDateTime.parse("2026-03-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewerId").value(secondReviewerId))
                .andExpect(jsonPath("$.data.reviewerName").value("재승인자"))
                .andExpect(jsonPath("$.data.reviewedAt").value(
                        org.hamcrest.Matchers.startsWith("2026-03-01")));
    }

    @Test
    void memberDetailWithNoReviewHistoryLeavesReviewFieldsNull() throws Exception {
        Long adminId = insertMember("admin14", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long pendingId = insertMember("student13", "대기학생", "STUDENT", "PENDING");

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", pendingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.action").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.reviewerId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.reviewerName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.reviewedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void withdrawnMemberDetailIsStillAccessible() throws Exception {
        Long adminId = insertMember("admin15", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long withdrawnId = insertMember("student14", "탈퇴학생", "STUDENT", "WITHDRAWN");

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", withdrawnId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void missingMemberDetailReturnsNotFound() throws Exception {
        Long adminId = insertMember("admin16", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentAndUnauthenticatedCannotGetMemberDetail() throws Exception {
        Long studentId = insertMember("student15", "학생", "STUDENT", "APPROVED");
        Long otherId = insertMember("student16", "학생2", "STUDENT", "APPROVED");
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", otherId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/members/{memberId}", otherId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberDetailDoesNotTriggerNPlusOneQueries() throws Exception {
        Long adminId = insertMember("admin17", "관리자", "ADMIN", "APPROVED");
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long reviewerId = insertMember("admin18", "심사자", "ADMIN", "APPROVED");
        Long targetId = insertMember("student17", "학생", "STUDENT", "APPROVED");
        insertReview(targetId, reviewerId, "APPROVED", null, OffsetDateTime.now());
        insertReview(targetId, reviewerId, "APPROVED", null, OffsetDateTime.now().plusDays(1));
        insertReview(targetId, reviewerId, "APPROVED", null, OffsetDateTime.now().plusDays(2));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 1) 토큰 버전 확인(#106), 2) 대상 회원 findById(엔티티 get, 미집계),
        // 3) 최신 심사 기록 조회, 4) 심사자 findById(엔티티 get, 미집계) = 실제 집계되는
        // JPQL @Query 실행은 1)+3) 두 건뿐이며 심사 기록이 몇 건 있든 고정이어야 한다.
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(2);
    }

    private void insertReview(Long memberId, Long reviewerId, String action, String rejectionReason,
                              OffsetDateTime reviewedAt) {
        jdbcTemplate.update("""
                INSERT INTO member_review (member_id, reviewer_id, action, rejection_reason, reviewed_at)
                VALUES (?, ?, ?, ?, ?)
                """, memberId, reviewerId, action, rejectionReason, reviewedAt);
    }

    private void assertStatusCount(String token, String status, int expectedCount) throws Exception {
        mockMvc.perform(get("/api/v1/admin/members")
                        .param("status", status)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.totalElements").value(expectedCount));
    }

    private Long insertMember(String studentNumber, String name, String role, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, ?, now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role, status);
    }
}
