package com.sat.lms.assignment.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.assignment.service.AssignmentService;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
class AssignmentPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lms_assignment_test")
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
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired AssignmentService assignmentService;
    @Autowired Clock applicationClock;
    @MockitoBean FileStorage fileStorage;
    AtomicInteger memberSequence = new AtomicInteger();

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

    @AfterEach
    void restoreAssignmentClock() {
        setAssignmentClock(applicationClock);
    }

    @Test
    void localDateTimeIsStoredAndReadAsTheSameAsiaSeoulInstant() throws Exception {
        setAssignmentClock(Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));
        Long adminId = insertMember("ADMIN");
        String responseBody = mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title":"시간대 검증","content":"내용",
                                 "dueAt":"2026-08-02T23:59:59","allowLateSubmission":false}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        long assignmentId = response.path("data").path("assignmentId").asLong();
        Instant expectedInstant = Instant.parse("2026-08-02T14:59:59Z");
        Object savedDueAt = jdbcTemplate.queryForObject(
                "SELECT due_at FROM assignment WHERE id = ?", Object.class, assignmentId);
        assertThat(toInstant(savedDueAt)).isEqualTo(expectedInstant);

        String detailBody = mockMvc.perform(get("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(OffsetDateTime.parse(objectMapper.readTree(detailBody).path("data").path("dueAt").asText())
                .toInstant()).isEqualTo(expectedInstant);
        assertThat(objectMapper.readTree(detailBody).path("data").path("attachments").isArray()).isTrue();
        assertThat(objectMapper.readTree(detailBody).path("data").path("attachments")).isEmpty();
    }

    @Test
    void actualAssignmentCrudWorks() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long studentId = insertMember("STUDENT");
        String adminToken = token(adminId, "ADMIN");
        String studentToken = token(studentId, "STUDENT");

        String responseBody = mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"title":"과제 1","content":"과제 내용",
                                 "dueAt":"2099-09-10T23:59:00","allowLateSubmission":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("과제 1"))
                .andExpect(jsonPath("$.data.allowLateSubmission").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        long assignmentId = response.path("data").path("assignmentId").asLong();
        assertThat(response.path("data").path("createdAt").asText()).isNotBlank();
        assertThat(response.path("data").path("updatedAt").asText()).isNotBlank();
        Map<String, Object> saved = jdbcTemplate.queryForMap("""
                SELECT title, content, allow_late_submission, due_at, created_at, updated_at
                FROM assignment WHERE id = ?
                """, assignmentId);
        assertThat(saved.get("title")).isEqualTo("과제 1");
        assertThat(saved.get("content")).isEqualTo("과제 내용");
        assertThat(saved.get("allow_late_submission")).isEqualTo(true);
        assertThat(toInstant(saved.get("due_at")))
                .isEqualTo(OffsetDateTime.parse("2099-09-10T23:59:00+09:00").toInstant());
        Instant originalCreatedAt = toInstant(saved.get("created_at"));
        Instant originalUpdatedAt = toInstant(saved.get("updated_at"));

        mockMvc.perform(get("/api/v1/assignments")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].assignmentId").value(assignmentId));
        mockMvc.perform(get("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("과제 내용"));

        String updateResponseBody = mockMvc.perform(patch("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"content":"수정 내용","allowLateSubmission":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("과제 1"))
                .andExpect(jsonPath("$.data.content").value("수정 내용"))
                .andExpect(jsonPath("$.data.allowLateSubmission").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode updateResponse = objectMapper.readTree(updateResponseBody);
        OffsetDateTime updatedCreatedAt = OffsetDateTime.parse(updateResponse.path("data").path("createdAt").asText());
        OffsetDateTime updatedAt = OffsetDateTime.parse(updateResponse.path("data").path("updatedAt").asText());
        assertThat(updatedCreatedAt.toInstant()).isEqualTo(originalCreatedAt);
        assertThat(updatedAt.toInstant()).isAfter(originalUpdatedAt);
        Map<String, Object> updated = jdbcTemplate.queryForMap("""
                SELECT title, content, allow_late_submission, due_at, created_at, updated_at
                FROM assignment WHERE id = ?
                """, assignmentId);
        assertThat(updated.get("title")).isEqualTo("과제 1");
        assertThat(updated.get("content")).isEqualTo("수정 내용");
        assertThat(updated.get("allow_late_submission")).isEqualTo(false);
        assertThat(toInstant(updated.get("due_at")))
                .isEqualTo(OffsetDateTime.parse("2099-09-10T23:59:00+09:00").toInstant());
        assertThat(toInstant(updated.get("created_at"))).isEqualTo(originalCreatedAt);

        mockMvc.perform(delete("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment WHERE id = ?",
                Long.class, assignmentId)).isZero();
        mockMvc.perform(get("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void pastDueAtReturnsBadRequestWithoutCreatingAssignmentRow() throws Exception {
        Long adminId = insertMember("ADMIN");

        mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"title":"과거 마감 과제","content":"내용",
                                 "dueAt":"2000-01-01T00:00:00","allowLateSubmission":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("마감 시각은 현재보다 미래여야 합니다."));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment", Long.class)).isZero();
    }

    @Test
    void pastDueAtUpdateKeepsExistingRowAndUpdatedAt() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long assignmentId = insertAssignment(adminId, "기존 과제", "2099-01-01T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z");
        Map<String, Object> before = jdbcTemplate.queryForMap(
                "SELECT title, due_at, updated_at FROM assignment WHERE id = ?", assignmentId);

        mockMvc.perform(patch("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN"))
                        .contentType("application/json")
                        .content("{\"title\":\"변경 시도\",\"dueAt\":\"2000-01-01T00:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("마감 시각은 현재보다 미래여야 합니다."));

        Map<String, Object> after = jdbcTemplate.queryForMap(
                "SELECT title, due_at, updated_at FROM assignment WHERE id = ?", assignmentId);
        assertThat(after.get("title")).isEqualTo(before.get("title"));
        assertThat(toInstant(after.get("due_at"))).isEqualTo(toInstant(before.get("due_at")));
        assertThat(toInstant(after.get("updated_at"))).isEqualTo(toInstant(before.get("updated_at")));
    }

    @Test
    void pagingDefaultAndAllowedSortsExecuteAgainstPostgresql() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long studentId = insertMember("STUDENT");
        String token = token(studentId, "STUDENT");
        insertAssignment(adminId, "A", "2026-09-04T00:00:00Z", "2026-08-01T00:00:00Z", "2026-08-04T00:00:00Z");
        insertAssignment(adminId, "B", "2026-09-01T00:00:00Z", "2026-08-02T00:00:00Z", "2026-08-03T00:00:00Z");
        insertAssignment(adminId, "C", "2026-09-03T00:00:00Z", "2026-08-03T00:00:00Z", "2026-08-02T00:00:00Z");
        insertAssignment(adminId, "D", "2026-09-02T00:00:00Z", "2026-08-04T00:00:00Z", "2026-08-01T00:00:00Z");

        mockMvc.perform(get("/api/v1/assignments?page=0&size=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("B"))
                .andExpect(jsonPath("$.data.content[1].title").value("D"))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        mockMvc.perform(get("/api/v1/assignments?page=1&size=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("C"))
                .andExpect(jsonPath("$.data.content[1].title").value("A"))
                .andExpect(jsonPath("$.data.totalElements").value(4));
        mockMvc.perform(get("/api/v1/assignments?sort=dueAt,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("B"));
        mockMvc.perform(get("/api/v1/assignments?sort=dueAt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("B"));
        mockMvc.perform(get("/api/v1/assignments?sort=dueAt,ASC")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("B"));
        mockMvc.perform(get("/api/v1/assignments?sort=")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("B"));
        mockMvc.perform(get("/api/v1/assignments?sort=updatedAt,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("A"));
        mockMvc.perform(get("/api/v1/assignments?sort=createdAt,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("A"));
        mockMvc.perform(get("/api/v1/assignments?sort=title,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value("D"));
        mockMvc.perform(get("/api/v1/assignments?sort=content,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?sort=dueAt,sideways")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?sort=createdAt,desc&sort=dueAt,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignmentWithSubmissionReturnsConflictOnDelete() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long studentId = insertMember("STUDENT");
        Long assignmentId = insertAssignment(adminId, "제출 과제", "2026-09-10T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO submission (assignment_id, student_id, text_content, is_late, created_at, updated_at)
                VALUES (?, ?, '제출', false, now(), now())
                """, assignmentId, studentId);
        String attachmentKey = "assignments/" + assignmentId + "/protected.pdf";
        Long attachmentId = insertAssignmentAttachment(assignmentId, attachmentKey);

        mockMvc.perform(delete("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment WHERE id = ?",
                Long.class, assignmentId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment_attachment WHERE attachment_id = ?",
                Long.class, attachmentId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?",
                Long.class, attachmentId)).isEqualTo(1L);
        verify(fileStorage, never()).delete(attachmentKey);
    }

    @Test
    void listProjectionDoesNotCauseAdminNPlusOne() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long studentId = insertMember("STUDENT");
        for (int index = 0; index < 5; index++) {
            insertAssignment(adminId, "과제" + index, "2026-09-10T00:00:00Z",
                    "2026-08-0" + (index + 1) + "T00:00:00Z",
                    "2026-08-0" + (index + 1) + "T00:00:00Z");
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/assignments?page=0&size=2")
                        .header("Authorization", "Bearer " + token(studentId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3L);
    }

    @Test
    void assignmentAttachmentUploadPersistsRowsAndDetailUsesOneAttachmentQuery() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long studentId = insertMember("STUDENT");
        Long assignmentId = insertAssignment(adminId, "첨부 과제", "2026-09-10T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
        String firstStored = "11111111-1111-1111-1111-111111111111.pdf";
        String secondStored = "22222222-2222-2222-2222-222222222222.hwpx";
        when(fileStorage.upload(any(), eq("assignments/" + assignmentId))).thenReturn(
                new StoredFile("안내.PDF", firstStored, "assignments/" + assignmentId + "/" + firstStored,
                        "pdf", 1L),
                new StoredFile("서식.HWPX", secondStored, "assignments/" + assignmentId + "/" + secondStored,
                        "hwpx", 2L));

        mockMvc.perform(multipart("/api/v1/assignments/{id}/attachments", assignmentId)
                        .file(multipartFile("안내.PDF", "첫 파일"))
                        .file(multipartFile("서식.HWPX", "두 번째 파일"))
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN"))
                        .characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].storedName").doesNotExist());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM assignment_attachment WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(2L);
        Map<String, Object> metadata = jdbcTemplate.queryForMap(
                "SELECT original_name, stored_name, storage_key, extension, size_kb FROM attachment ORDER BY id LIMIT 1");
        assertThat(metadata.get("original_name")).isEqualTo("안내.PDF");
        assertThat(metadata.get("stored_name")).isEqualTo(firstStored);
        assertThat(metadata.get("storage_key")).isEqualTo("assignments/" + assignmentId + "/" + firstStored);
        assertThat(metadata.get("extension")).isEqualTo("pdf");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + token(studentId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachments.length()").value(2))
                .andExpect(jsonPath("$.data.attachments[0].originalName").value("안내.PDF"))
                .andExpect(jsonPath("$.data.attachments[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data.attachments[0].storedName").doesNotExist())
                .andExpect(jsonPath("$.data.attachments[0].downloadUrl").doesNotExist());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3L);
    }

    @Test
    void cumulativeLimitRejectsTwoPlusTwoAndAllowsTwoPlusOne() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long assignmentId = insertAssignment(adminId, "누적 과제", "2026-09-10T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
        insertAssignmentAttachment(assignmentId, "assignments/" + assignmentId + "/existing-a.pdf");
        insertAssignmentAttachment(assignmentId, "assignments/" + assignmentId + "/existing-b.pdf");

        mockMvc.perform(multipart("/api/v1/assignments/{id}/attachments", assignmentId)
                        .file(multipartFile("new-a.pdf", "a")).file(multipartFile("new-b.pdf", "b"))
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("과제 첨부파일은 최대 3개까지 등록할 수 있습니다."));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(2L);
        verify(fileStorage, never()).upload(any(), anyString());

        String stored = "33333333-3333-3333-3333-333333333333.pdf";
        when(fileStorage.upload(any(), eq("assignments/" + assignmentId))).thenReturn(new StoredFile(
                "new.pdf", stored, "assignments/" + assignmentId + "/" + stored, "pdf", 1L));
        mockMvc.perform(multipart("/api/v1/assignments/{id}/attachments", assignmentId)
                        .file(multipartFile("new.pdf", "new"))
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM assignment_attachment WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(3L);
    }

    @Test
    void assignmentAttachmentDatabaseFailureRollsBackRowsAndCompensatesStorage() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long assignmentId = insertAssignment(adminId, "업로드 롤백", "2026-09-10T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
        String stored = "44444444-4444-4444-4444-444444444444.pdf";
        String key = "assignments/" + assignmentId + "/" + stored;
        when(fileStorage.upload(any(), eq("assignments/" + assignmentId))).thenReturn(
                new StoredFile("a.pdf", stored, key, "pdf", 1L),
                new StoredFile("b.pdf", stored, key, "pdf", 1L));

        mockMvc.perform(multipart("/api/v1/assignments/{id}/attachments", assignmentId)
                        .file(multipartFile("a.pdf", "a")).file(multipartFile("b.pdf", "b"))
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment_attachment", Long.class)).isZero();
        org.mockito.Mockito.verify(fileStorage, org.mockito.Mockito.times(2)).delete(key);
    }

    @Test
    void downloadRejectsNoticeAttachmentAndNormalDeleteRunsAfterCommit() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long studentId = insertMember("STUDENT");
        Long assignmentId = insertAssignment(adminId, "다운로드 과제", "2026-09-10T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
        Long attachmentId = insertAssignmentAttachment(assignmentId,
                "assignments/" + assignmentId + "/normal.pdf");
        Long noticeId = jdbcTemplate.queryForObject("""
                INSERT INTO notice (admin_id, title, content, is_pinned, created_at, updated_at)
                VALUES (?, '공지', '내용', false, now(), now()) RETURNING id
                """, Long.class, adminId);
        Long noticeAttachmentId = insertAttachment("notices/" + noticeId + "/notice.pdf");
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, noticeAttachmentId);
        Long submissionId = jdbcTemplate.queryForObject("""
                INSERT INTO submission (assignment_id, student_id, text_content, is_late, created_at, updated_at)
                VALUES (?, ?, '제출', false, now(), now()) RETURNING id
                """, Long.class, assignmentId, studentId);
        Long submissionAttachmentId = insertAttachment("submissions/" + submissionId + "/submission.pdf");
        jdbcTemplate.update("INSERT INTO submission_attachment (submission_id, attachment_id) VALUES (?, ?)",
                submissionId, submissionAttachmentId);

        mockMvc.perform(get("/api/v1/assignment-attachments/{id}/download-url", noticeAttachmentId)
                        .header("Authorization", "Bearer " + token(studentId, "STUDENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/assignment-attachments/{id}/download-url", submissionAttachmentId)
                        .header("Authorization", "Bearer " + token(studentId, "STUDENT")))
                .andExpect(status().isNotFound());
        verify(fileStorage, never()).createDownloadUrl(anyString());

        mockMvc.perform(delete("/api/v1/assignment-attachments/{id}", attachmentId)
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?",
                Long.class, attachmentId)).isZero();
        verify(fileStorage).delete("assignments/" + assignmentId + "/normal.pdf");
    }

    @Test
    void deletingAssignmentCleansUnsharedAttachmentsButProtectsSharedOnes() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long assignmentId = insertAssignment(adminId, "삭제 과제", "2026-09-10T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
        String unsharedKey = "assignments/" + assignmentId + "/unshared.pdf";
        String sharedKey = "assignments/" + assignmentId + "/shared.pdf";
        Long unsharedId = insertAssignmentAttachment(assignmentId, unsharedKey);
        Long sharedId = insertAssignmentAttachment(assignmentId, sharedKey);
        Long noticeId = jdbcTemplate.queryForObject("""
                INSERT INTO notice (admin_id, title, content, is_pinned, created_at, updated_at)
                VALUES (?, '공유 공지', '내용', false, now(), now()) RETURNING id
                """, Long.class, adminId);
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, sharedId);

        mockMvc.perform(delete("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment WHERE id = ?",
                Long.class, assignmentId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?",
                Long.class, unsharedId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?",
                Long.class, sharedId)).isEqualTo(1L);
        verify(fileStorage).delete(unsharedKey);
        verify(fileStorage, never()).delete(sharedKey);
    }

    @Test
    void assignmentDeleteDatabaseFailureRollsBackAllRowsAndStorage() throws Exception {
        Long adminId = insertMember("ADMIN");
        Long assignmentId = insertAssignment(adminId, "롤백 과제", "2026-09-10T00:00:00Z",
                "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
        String key = "assignments/" + assignmentId + "/rollback.pdf";
        Long attachmentId = insertAssignmentAttachment(assignmentId, key);
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_assignment_attachment_delete() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced delete failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_assignment_attachment_delete
                BEFORE DELETE ON assignment_attachment
                FOR EACH ROW EXECUTE FUNCTION fail_assignment_attachment_delete()
                """);
        try {
            mockMvc.perform(delete("/api/v1/assignments/{id}", assignmentId)
                            .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                    .andExpect(status().is5xxServerError());
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment WHERE id = ?",
                    Long.class, assignmentId)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment_attachment WHERE attachment_id = ?",
                    Long.class, attachmentId)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?",
                    Long.class, attachmentId)).isEqualTo(1L);
            verify(fileStorage, never()).delete(key);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_assignment_attachment_delete ON assignment_attachment");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_assignment_attachment_delete()");
        }
    }

    private Long insertMember(String role) {
        int sequence = memberSequence.incrementAndGet();
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'APPROVED', now(), now()) RETURNING id
                """, Long.class, String.format("%08d", sequence), role + sequence, role);
    }

    private Long insertAssignment(Long adminId, String title, String dueAt, String createdAt, String updatedAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment (admin_id, title, content, due_at, allow_late_submission,
                                        created_at, updated_at)
                VALUES (?, ?, '내용', ?::timestamptz, false, ?::timestamptz, ?::timestamptz) RETURNING id
                """, Long.class, adminId, title, dueAt, createdAt, updatedAt);
    }

    private Long insertAssignmentAttachment(Long assignmentId, String storageKey) {
        Long attachmentId = insertAttachment(storageKey);
        jdbcTemplate.update("INSERT INTO assignment_attachment (assignment_id, attachment_id) VALUES (?, ?)",
                assignmentId, attachmentId);
        return attachmentId;
    }

    private Long insertAttachment(String storageKey) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO attachment (original_name, stored_name, storage_key, extension, size_kb, created_at)
                VALUES ('original.pdf', 'stored.pdf', ?, 'pdf', 1, now()) RETURNING id
                """, Long.class, storageKey);
    }

    private MockMultipartFile multipartFile(String originalName, String content) {
        return new MockMultipartFile("files", originalName, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private String token(Long memberId, String role) {
        return jwtTokenProvider.createAccessToken(memberId, role);
    }

    private void setAssignmentClock(Clock clock) {
        Object target = AopTestUtils.getTargetObject(assignmentService);
        ReflectionTestUtils.setField(target, "clock", clock);
    }

    private Instant toInstant(Object timestamp) {
        if (timestamp instanceof OffsetDateTime value) return value.toInstant();
        return ((Timestamp) timestamp).toInstant();
    }
}
