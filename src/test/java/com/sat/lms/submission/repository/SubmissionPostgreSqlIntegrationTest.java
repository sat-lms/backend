package com.sat.lms.submission.repository;

import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileExtensionExtractor;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
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
            return new StoredFile(file.getOriginalFilename(), storedName, directory + "/" + storedName,
                    FileExtensionExtractor.extract(file.getOriginalFilename()), 1L);
        });
    }

    @Test
    void studentFirstSubmissionWithTextAndFilesPersistsAttachmentsAndReturnsCreated() throws Exception {
        Long studentId = insertMember("student01", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        MockMultipartFile request = jsonPart("Member 클래스를 구현했습니다.");
        MockMultipartFile file = new MockMultipartFile("files", "Member.JAVA", "text/plain", "code".getBytes());

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(request).file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.textContent").value("Member 클래스를 구현했습니다."))
                .andExpect(jsonPath("$.data.isLate").value(false))
                .andExpect(jsonPath("$.data.files[0].originalName").value("Member.JAVA"));

        assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT extension FROM attachment", String.class)).isEqualTo("java");
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

    @Test
    void resubmitReplacesTextAndFilesAndDeletesOldS3Object() throws Exception {
        Long studentId = insertMember("student08", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        MockMultipartFile oldFile = new MockMultipartFile("files", "old.txt", "text/plain", "old".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("첫 제출")).file(oldFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        String oldStorageKey = attachmentRepository.findAll().get(0).getStorageKey();

        MockMultipartFile newFile = new MockMultipartFile("files", "new.txt", "text/plain", "new".getBytes());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("수정된 제출")).file(newFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.textContent").value("수정된 제출"))
                .andExpect(jsonPath("$.data.files[0].originalName").value("new.txt"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(1);
        verify(fileStorage).delete(oldStorageKey);
    }

    @Test
    void resubmitWithoutExistingSubmissionReturnsNotFound() throws Exception {
        Long studentId = insertMember("student09", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("재제출"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void resubmitBlockedAfterDeadlineWithoutLateAllowance() throws Exception {
        Long studentId = insertMember("student10", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), true);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("첫 제출"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        jdbcTemplate.update("UPDATE assignment SET due_at = now() - interval '1 day', "
                + "allow_late_submission = false WHERE id = ?", assignmentId);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("재제출 시도"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSubmissionRemovesAttachmentsAndCallsFileStorage() throws Exception {
        Long studentId = insertMember("student11", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출")).file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        String storageKey = attachmentRepository.findAll().get(0).getStorageKey();

        mockMvc.perform(delete("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(0);
        verify(fileStorage).delete(storageKey);
    }

    @Test
    void deleteSubmissionMissingReturnsNotFound() throws Exception {
        Long studentId = insertMember("student12", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(delete("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadUrlSucceedsForOwnerAndAdmin() throws Exception {
        Long studentId = insertMember("student13", "학생", "STUDENT");
        Long adminId = insertMember("admin13", "관리자", "ADMIN");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출")).file(file)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());
        Long attachmentId = attachmentRepository.findAll().get(0).getId();
        when(fileStorage.createDownloadUrl(anyString()))
                .thenReturn(new DownloadUrl("https://example.com/signed", 300L));

        mockMvc.perform(get("/api/v1/submission-attachments/{attachmentId}/download-url", attachmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://example.com/signed"))
                .andExpect(jsonPath("$.data.originalName").value("a.txt"));

        mockMvc.perform(get("/api/v1/submission-attachments/{attachmentId}/download-url", attachmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void downloadUrlForbiddenForOtherStudent() throws Exception {
        Long studentId = insertMember("student14", "학생", "STUDENT");
        Long otherId = insertMember("student15", "다른학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        String otherToken = jwtTokenProvider.createAccessToken(otherId, "STUDENT");
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출")).file(file)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());
        Long attachmentId = attachmentRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/v1/submission-attachments/{attachmentId}/download-url", attachmentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAttachmentSucceedsWhenOtherFileRemains() throws Exception {
        Long studentId = insertMember("student16", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        MockMultipartFile fileA = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MockMultipartFile fileB = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart(null)).file(fileA).file(fileB)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        Long attachmentIdToDelete = attachmentRepository.findAll().stream()
                .filter(a -> a.getOriginalName().equals("a.txt")).findFirst().orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/submission-attachments/{attachmentId}", attachmentIdToDelete)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(1);
    }

    @Test
    void deleteAttachmentBlockedWhenResultingSubmissionWouldBeEmpty() throws Exception {
        Long studentId = insertMember("student17", "학생", "STUDENT");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart(null)).file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        Long attachmentId = attachmentRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/v1/submission-attachments/{attachmentId}", attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(1);
    }

    @Test
    void adminAndUnauthenticatedCannotPerformStudentOnlySubmissionActions() throws Exception {
        Long studentId = insertMember("student18", "학생", "STUDENT");
        Long adminId = insertMember("admin18", "관리자", "ADMIN");
        Long assignmentId = insertAssignment(OffsetDateTime.now().plusDays(1), false);
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("제출"))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("관리자 수정"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("무인증 수정")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/assignments/{assignmentId}/submission", assignmentId))
                .andExpect(status().isUnauthorized());
    }

    private MockMultipartFile jsonPart(String textContent) throws Exception {
        String body = textContent == null ? "{}" : "{\"textContent\":\"" + textContent + "\"}";
        return new MockMultipartFile("request", "request", "application/json",
                body.getBytes(StandardCharsets.UTF_8));
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
