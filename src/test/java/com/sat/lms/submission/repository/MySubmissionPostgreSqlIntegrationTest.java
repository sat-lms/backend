package com.sat.lms.submission.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
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
import org.springframework.http.HttpMethod;
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
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
@SpringBootTest
class MySubmissionPostgreSqlIntegrationTest {
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
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired SubmissionAttachmentRepository submissionAttachmentRepository;
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
    void multipleSubmissionsAreListedWithPagination() throws Exception {
        Long studentId = insertMember("student01", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        submitText(studentId, token, insertAssignment("과제1"), "내용1");
        submitText(studentId, token, insertAssignment("과제2"), "내용2");
        submitText(studentId, token, insertAssignment("과제3"), "내용3");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void emptyListIsReturnedWhenStudentHasNoSubmissions() throws Exception {
        Long studentId = insertMember("student02", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void onlyOwnSubmissionsAreReturned() throws Exception {
        Long studentAId = insertMember("student03", "학생A", "STUDENT");
        Long studentBId = insertMember("student04", "학생B", "STUDENT");
        String tokenA = jwtTokenProvider.createAccessToken(studentAId, "STUDENT");
        String tokenB = jwtTokenProvider.createAccessToken(studentBId, "STUDENT");
        Long assignmentId = insertAssignment("공용과제");
        submitText(studentAId, tokenA, assignmentId, "A의 제출");
        submitText(studentBId, tokenB, insertAssignment("과제B전용"), "B의 제출");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("includeNotSubmitted", "false")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].textContent").value("A의 제출"));
    }

    @Test
    void submissionsWithAndWithoutAttachmentsAreHandledCorrectly() throws Exception {
        Long studentId = insertMember("student05", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long assignmentWithFileId = insertAssignment("파일있는과제");
        Long assignmentWithoutFileId = insertAssignment("파일없는과제");
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain",
                "a".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentWithFileId)
                        .file(jsonPart("파일 제출")).file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        submitText(studentId, token, assignmentWithoutFileId, "텍스트만 제출");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].attachments.length()").value(0))
                .andExpect(jsonPath("$.data.content[1].attachments.length()").value(1))
                .andExpect(jsonPath("$.data.content[1].attachments[0].originalName").value("a.txt"));
    }

    @Test
    void adminAndUnauthenticatedCannotListMySubmissions() throws Exception {
        Long adminId = insertMember("admin01", "관리자", "ADMIN");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/members/me/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resultsAreOrderedByCreatedAtDescendingByDefault() throws Exception {
        Long studentId = insertMember("student06", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long assignmentOldId = insertAssignment("오래된과제");
        Long assignmentNewId = insertAssignment("최신과제");
        submitText(studentId, token, assignmentOldId, "오래된 제출");
        submitText(studentId, token, assignmentNewId, "최신 제출");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].textContent").value("최신 제출"))
                .andExpect(jsonPath("$.data.content[1].textContent").value("오래된 제출"));
    }

    @Test
    void resultOrderStaysDeterministicAcrossRepeatedRequestsAndResubmitEvenWithTiedTimestamps() throws Exception {
        Long studentId = insertMember("student10", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long assignmentAId = insertAssignment("과제A");
        Long assignmentBId = insertAssignment("과제B");
        Long assignmentCId = insertAssignment("과제C");
        submitText(studentId, token, assignmentAId, "A 제출");
        submitText(studentId, token, assignmentBId, "B 제출");
        submitText(studentId, token, assignmentCId, "C 제출");
        jdbcTemplate.update(
                "UPDATE submission SET created_at = '2026-01-01T00:00:00Z' WHERE student_id = ?", studentId);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/assignments/{assignmentId}/submission", assignmentBId)
                        .file(jsonPart("B 재제출"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<Long> expectedOrder = jdbcTemplate.queryForList(
                "SELECT id FROM submission WHERE student_id = ? ORDER BY id DESC", Long.class, studentId);

        for (int attempt = 0; attempt < 3; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/v1/members/me/submissions")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("data").get("content");
            List<Long> actualOrder = new ArrayList<>();
            content.forEach(node -> actualOrder.add(node.get("submissionId").asLong()));
            assertThat(actualOrder).isEqualTo(expectedOrder);
        }
    }

    @Test
    void listingWithAttachmentsDoesNotTriggerNPlusOneQueries() throws Exception {
        Long studentId = insertMember("student07", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        for (int i = 0; i < 5; i++) {
            MockMultipartFile file = new MockMultipartFile("files", "f" + i + ".txt", "text/plain",
                    "x".getBytes(StandardCharsets.UTF_8));
            mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", insertAssignment("과제" + i))
                            .file(jsonPart("제출" + i)).file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        assertThat(statistics.getQueryExecutionCount()).isEqualTo(3);
    }

    @Test
    void allNotSubmittedPageSkipsAttachmentQueryAndExecutesOnlyContentAndCountQueries() throws Exception {
        Long studentId = insertMember("student15", "student", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        for (int i = 0; i < 5; i++) insertAssignment("missing-" + i);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.content[0].submissionId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].attachments.length()").value(0))
                .andExpect(jsonPath("$.data.content[0].fileNames.length()").value(0));

        assertThat(statistics.getQueryExecutionCount()).isEqualTo(2);
    }

    @Test
    void assignmentBasedListIncludesMissingRowsWithoutLeakingAnotherStudentsSubmissionAndMatchesApi23Status()
            throws Exception {
        Long studentId = insertMember("student11", "student", "STUDENT");
        Long otherId = insertMember("student12", "other", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long futureId = insertAssignment("future");
        Long closedId = insertAssignment("closed");
        Long ownId = insertAssignment("own");
        Long otherOnlyId = insertAssignment("other-only");
        jdbcTemplate.update("UPDATE assignment SET due_at=now()-interval '1 day', allow_late_submission=false WHERE id=?",
                closedId);
        insertSubmission(ownId, studentId, false, "own text", OffsetDateTime.now().minusHours(1));
        insertSubmission(otherOnlyId, otherId, false, "secret text", OffsetDateTime.now().minusHours(1));

        MvcResult result = mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andReturn();
        Map<Long, JsonNode> rows = rowsByAssignment(result);
        assertThat(rows.get(futureId).get("submissionStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(rows.get(closedId).get("submissionStatus").asText()).isEqualTo("NOT_SUBMITTED");
        assertThat(rows.get(ownId).get("submissionStatus").asText()).isEqualTo("SUBMITTED");
        assertThat(rows.get(otherOnlyId).get("submissionId").isNull()).isTrue();
        assertThat(rows.get(otherOnlyId).get("textContent").isNull()).isTrue();
        assertThat(rows.get(otherOnlyId).get("fileNames").isEmpty()).isTrue();
        assertThat(rows.get(otherOnlyId).get("attachments").isEmpty()).isTrue();

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("includeNotSubmitted", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        MvcResult assignments = mockMvc.perform(get("/api/v1/assignments")
                        .param("size", "20").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Map<Long, JsonNode> assignmentRows = rowsByAssignment(assignments);
        rows.forEach((id, row) -> assertThat(assignmentRows.get(id).get("submissionStatus").asText())
                .isEqualTo(row.get("submissionStatus").asText()));
    }

    @Test
    void fixedDatabaseSortsHaveDeterministicTieBreakersAndSubmittedNullsLast() throws Exception {
        Long studentId = insertMember("student13", "student", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long a = insertAssignment("a");
        Long b = insertAssignment("b");
        Long c = insertAssignment("c");
        OffsetDateTime tiedDueAt = OffsetDateTime.parse("2030-01-01T00:00:00Z");
        jdbcTemplate.update("UPDATE assignment SET due_at=? WHERE id in (?,?,?)", tiedDueAt, a, b, c);
        OffsetDateTime tiedSubmittedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        insertSubmission(a, studentId, false, "a", tiedSubmittedAt);
        insertSubmission(b, studentId, true, "b", tiedSubmittedAt);

        assertThat(fetchAssignmentIds(token, "dueAtDesc")).containsExactly(c, b, a);
        assertThat(fetchAssignmentIds(token, "dueAtAsc")).containsExactly(a, b, c);
        assertThat(fetchAssignmentIds(token, "submittedAtDesc")).containsExactly(b, a, c);
    }

    @Test
    void submittedAtTracksResubmissionUpdatedAtAndFileNamesFollowAttachmentOrder() throws Exception {
        Long studentId = insertMember("student14", "student", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long assignmentId = insertAssignment("resubmit-time");
        insertSubmission(assignmentId, studentId, false, "first",
                OffsetDateTime.parse("2025-01-01T00:00:00Z"));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart("second")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        Long submissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM submission WHERE assignment_id=? AND student_id=?", Long.class,
                assignmentId, studentId);
        insertAttachment(submissionId, "first.pdf");
        insertAttachment(submissionId, "second.pdf");

        MvcResult result = mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode row = rowsByAssignment(result).get(assignmentId);
        assertThat(row.get("submittedAt").asText()).isEqualTo(row.get("updatedAt").asText());
        assertThat(OffsetDateTime.parse(row.get("submittedAt").asText()))
                .isAfter(OffsetDateTime.parse(row.get("createdAt").asText()));
        assertThat(objectMapper.convertValue(row.get("fileNames"), List.class))
                .containsExactly("first.pdf", "second.pdf");
    }

    private void submitText(Long studentId, String token, Long assignmentId, String textContent) throws Exception {
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart(textContent))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void insertSubmission(Long assignmentId, Long studentId, boolean late, String text,
                                  OffsetDateTime submittedAt) {
        jdbcTemplate.update("""
                INSERT INTO submission(assignment_id,student_id,text_content,is_late,created_at,updated_at)
                VALUES (?,?,?,?,?,?)
                """, assignmentId, studentId, text, late, submittedAt, submittedAt);
    }

    private void insertAttachment(Long submissionId, String originalName) {
        Long attachmentId = jdbcTemplate.queryForObject("""
                INSERT INTO attachment(original_name,stored_name,storage_key,extension,size_kb,created_at)
                VALUES (?, ?, ?, 'pdf', 1, now()) RETURNING id
                """, Long.class, originalName, "stored-" + originalName, "test/" + submissionId + "/" + originalName);
        jdbcTemplate.update("INSERT INTO submission_attachment(submission_id,attachment_id) VALUES (?,?)",
                submissionId, attachmentId);
    }

    private Map<Long, JsonNode> rowsByAssignment(MvcResult result) throws Exception {
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("content");
        List<JsonNode> rows = new ArrayList<>();
        content.forEach(rows::add);
        return rows.stream().collect(Collectors.toMap(row -> row.get("assignmentId").asLong(), row -> row));
    }

    private List<Long> fetchAssignmentIds(String token, String sort) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("sort", sort).param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("content")
                .forEach(row -> ids.add(row.get("assignmentId").asLong()));
        return ids;
    }

    private MockMultipartFile jsonPart(String textContent) {
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

    private Long insertAssignment(String title) {
        Long adminId = insertMember("adm" + adminCounter.incrementAndGet(), "과제관리자", "ADMIN");
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment (admin_id, title, content, due_at, allow_late_submission,
                                        created_at, updated_at)
                VALUES (?, ?, '내용', ?, false, now(), now()) RETURNING id
                """, Long.class, adminId, title, OffsetDateTime.now().plusDays(1).withOffsetSameInstant(ZoneOffset.UTC));
    }
}
