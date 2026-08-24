package com.sat.lms.submission.repository;

import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
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
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
class SubmissionPostgreSqlIntegrationTest {
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
    @Autowired SubmissionRepository submissionRepository;
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired SubmissionAttachmentRepository submissionAttachmentRepository;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
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
    void studentFirstSubmissionWithTextAndFilesPersistsAttachmentsAndReturnsCreated() throws Exception {
        Long studentId = insertMember("student01", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        MockMultipartFile request = jsonPart("Member 클래스를 구현했습니다.");
        MockMultipartFile file = new MockMultipartFile("files", "Member.java", "text/plain", "code".getBytes());

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(request).file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.textContent").value("Member 클래스를 구현했습니다."))
                .andExpect(jsonPath("$.data.isLate").value(false))
                .andExpect(jsonPath("$.data.files[0].originalName").value("Member.java"));

        assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission_attachment", Long.class))
                .isEqualTo(1);
    }

    @Test
    void duplicateFirstSubmissionReturnsConflict() throws Exception {
        Long studentId = insertMember("student02", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        MockMultipartFile request = jsonPart("첫 제출");

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(request).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("재시도")).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void lateSubmissionIsBlockedWhenAssignmentDoesNotAllowLateSubmission() throws Exception {
        Long studentId = insertMember("student03", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().minusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("지각 제출")).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).isEmpty();
    }

    @Test
    void lateSubmissionIsPersistedWhenAssignmentAllowsLateSubmission() throws Exception {
        Long studentId = insertMember("student04", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().minusDays(1), true);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("지각 제출")).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isLate").value(true));
    }

    @Test
    void adminSubmissionAttemptIsForbidden() throws Exception {
        Long adminId = insertMember("admin01", "관리자", "ADMIN");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(adminId, "ADMIN");

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("관리자 제출")).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedSubmissionAttemptIsUnauthorized() throws Exception {
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("무인증 제출")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyTextAndFilesReturnsBadRequest() throws Exception {
        Long studentId = insertMember("student05", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart(null)).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingSubmissionReturnsNotFoundOnRead() throws Exception {
        Long studentId = insertMember("student06", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCanReadOwnSubmissionAfterSubmitting() throws Exception {
        Long studentId = insertMember("student07", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출 내용")).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.textContent").value("제출 내용"));
    }

    private MockMultipartFile jsonPart(String textContent) throws Exception {
        String body = textContent == null ? "{}" : "{\"textContent\":\"" + textContent + "\"}";
        return new MockMultipartFile("request", "request", "application/json", body.getBytes());
    }

    private Long insertMember(String studentNumber, String name, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'APPROVED', now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role);
    }

    private Long insertAssignment(OffsetDateTime dueAt, boolean allowLateSubmission) {
        Long adminId = insertMember("adm" + adminCounter.incrementAndGet(), "과제관리자", "ADMIN");
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment (admin_id, title, content, due_at, allow_late_submission,
                                        created_at, updated_at)
                VALUES (?, '과제', '내용', ?, ?, now(), now()) RETURNING id
                """, Long.class, adminId, dueAt.withOffsetSameInstant(ZoneOffset.UTC), allowLateSubmission);
    }
}
