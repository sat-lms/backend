package com.sat.lms.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
class AdminSubmissionPostgreSqlIntegrationTest {
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
    @Autowired ObjectMapper objectMapper;
    @MockitoBean FileStorage fileStorage;
    private final AtomicInteger adminCounter = new AtomicInteger();

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

        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            String directory = invocation.getArgument(1);
            String storedName = "stub-" + file.getOriginalFilename();
            return new StoredFile(file.getOriginalFilename(), storedName, directory + "/" + storedName, "txt", 1L);
        });
    }

    @Test
    void aggregatesAndListsSubmittedNotSubmittedAndLateStudentsCorrectly() throws Exception {
        Long adminId = insertMember("admin01", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long assignmentId = insertAssignment(adminId);
        Long onTimeId = insertMember("stdOnTime", "정시학생", "STUDENT", "APPROVED");
        Long lateId = insertMember("stdLate", "지각학생", "STUDENT", "APPROVED");
        Long notSubmittedId1 = insertMember("stdNone1", "미제출1", "STUDENT", "APPROVED");
        Long notSubmittedId2 = insertMember("stdNone2", "미제출2", "STUDENT", "APPROVED");
        insertSubmission(assignmentId, onTimeId, "정시 제출", false);
        insertSubmission(assignmentId, lateId, "지각 제출", true);

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onTimeSubmittedCount").value(1))
                .andExpect(jsonPath("$.data.lateSubmittedCount").value(1))
                .andExpect(jsonPath("$.data.notSubmittedCount").value(2))
                .andExpect(jsonPath("$.data.students.totalElements").value(4));

        assertThat(notSubmittedId1).isNotNull();
        assertThat(notSubmittedId2).isNotNull();
    }

    @Test
    void countsAreMutuallyExclusiveAndSumToApprovedStudentTotal() throws Exception {
        Long adminId = insertMember("admin09", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long assignmentId = insertAssignment(adminId);
        Long onTimeId = insertMember("stdSumOn", "정시학생", "STUDENT", "APPROVED");
        Long lateId = insertMember("stdSumLa", "지각학생", "STUDENT", "APPROVED");
        insertMember("stdSumN1", "미제출1", "STUDENT", "APPROVED");
        insertMember("stdSumN2", "미제출2", "STUDENT", "APPROVED");
        insertMember("stdSumN3", "미제출3", "STUDENT", "APPROVED");
        insertSubmission(assignmentId, onTimeId, "정시 제출", false);
        insertSubmission(assignmentId, lateId, "지각 제출", true);
        Long approvedStudentTotal = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM member WHERE role = 'STUDENT' AND status = 'APPROVED'", Long.class);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        long onTime = data.get("onTimeSubmittedCount").asLong();
        long late = data.get("lateSubmittedCount").asLong();
        long notSubmitted = data.get("notSubmittedCount").asLong();

        assertThat(onTime).isEqualTo(1L);
        assertThat(late).isEqualTo(1L);
        assertThat(notSubmitted).isEqualTo(3L);
        assertThat(onTime + late + notSubmitted).isEqualTo(approvedStudentTotal);
    }

    @Test
    void statusFilterReturnsOnlyMatchingStudents() throws Exception {
        Long adminId = insertMember("admin02", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long assignmentId = insertAssignment(adminId);
        Long onTimeId = insertMember("stdOnTim2", "정시학생", "STUDENT", "APPROVED");
        Long lateId = insertMember("stdLate2", "지각학생", "STUDENT", "APPROVED");
        insertMember("stdNone3", "미제출3", "STUDENT", "APPROVED");
        insertSubmission(assignmentId, onTimeId, "정시 제출", false);
        insertSubmission(assignmentId, lateId, "지각 제출", true);

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .param("status", "SUBMITTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.totalElements").value(2));

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .param("status", "NOT_SUBMITTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.totalElements").value(1));

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .param("status", "LATE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.totalElements").value(1))
                .andExpect(jsonPath("$.data.students.content[0].studentNumber").value("stdLate2"));
    }

    @Test
    void nonApprovedStudentsAreExcludedFromAggregation() throws Exception {
        Long adminId = insertMember("admin03", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long assignmentId = insertAssignment(adminId);
        insertMember("stdPend", "대기학생", "STUDENT", "PENDING");
        insertMember("stdRej", "거절학생", "STUDENT", "REJECTED");
        insertMember("stdWith", "탈퇴학생", "STUDENT", "WITHDRAWN");

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onTimeSubmittedCount").value(0))
                .andExpect(jsonPath("$.data.lateSubmittedCount").value(0))
                .andExpect(jsonPath("$.data.notSubmittedCount").value(0))
                .andExpect(jsonPath("$.data.students.totalElements").value(0));
    }

    @Test
    void missingAssignmentReturnsNotFound() throws Exception {
        Long adminId = insertMember("admin04", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", 999999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentAndUnauthenticatedCannotAccessSubmissionStatus() throws Exception {
        Long adminId = insertMember("admin05", "관리자", "ADMIN", "APPROVED");
        Long assignmentId = insertAssignment(adminId);
        Long studentId = insertMember("std05", "학생", "STUDENT", "APPROVED");
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingSubmissionStatusDoesNotTriggerNPlusOneQueries() throws Exception {
        Long adminId = insertMember("admin06", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long assignmentId = insertAssignment(adminId);
        for (int i = 0; i < 10; i++) {
            Long studentId = insertMember("stdN" + i, "학생" + i, "STUDENT", "APPROVED");
            if (i % 2 == 0) {
                insertSubmission(assignmentId, studentId, "제출" + i, false);
            }
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", assignmentId)
                        .param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.totalElements").value(10));

        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(6);
    }

    @Test
    void getSubmissionDetailReturnsFullInfoIncludingFiles() throws Exception {
        Long adminId = insertMember("admin07", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long studentId = insertMember("std07", "학생칠", "STUDENT", "APPROVED");
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long assignmentId = insertAssignment(adminId);
        MockMultipartFile file = new MockMultipartFile("files", "result.txt", "text/plain", "ok".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출 내용")).file(file)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());
        Long submissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM submission WHERE assignment_id = ? AND student_id = ?",
                Long.class, assignmentId, studentId);

        mockMvc.perform(get("/api/v1/admin/submissions/{submissionId}", submissionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(submissionId))
                .andExpect(jsonPath("$.data.studentNumber").value("std07"))
                .andExpect(jsonPath("$.data.textContent").value("제출 내용"))
                .andExpect(jsonPath("$.data.files[0].originalName").value("result.txt"));
    }

    @Test
    void getSubmissionDetailMissingReturnsNotFound() throws Exception {
        Long adminId = insertMember("admin08", "관리자", "ADMIN", "APPROVED");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");

        mockMvc.perform(get("/api/v1/admin/submissions/{submissionId}", 999999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentAndUnauthenticatedCannotAccessSubmissionDetail() throws Exception {
        Long studentId = insertMember("std09", "학생구", "STUDENT", "APPROVED");
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/submissions/{submissionId}", 1L)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/submissions/{submissionId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    private MockMultipartFile jsonPart(String textContent) {
        String body = textContent == null ? "{}" : "{\"textContent\":\"" + textContent + "\"}";
        return new MockMultipartFile("request", "request", "application/json", body.getBytes());
    }

    private Long insertMember(String studentNumber, String name, String role, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, ?, now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role, status);
    }

    private Long insertAssignment(Long adminId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment (admin_id, title, content, due_at, allow_late_submission,
                                        created_at, updated_at)
                VALUES (?, '과제', '내용', ?, true, now(), now()) RETURNING id
                """, Long.class, adminId,
                OffsetDateTime.now().plusDays(1).withOffsetSameInstant(ZoneOffset.UTC));
    }

    private Long insertSubmission(Long assignmentId, Long studentId, String textContent, boolean late) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission (assignment_id, student_id, text_content, is_late, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now()) RETURNING id
                """, Long.class, assignmentId, studentId, textContent, late);
    }
}
