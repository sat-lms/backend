package com.sat.lms.submission.repository;

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
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
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
        MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentWithFileId)
                        .file(jsonPart("파일 제출")).file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        submitText(studentId, token, assignmentWithoutFileId, "텍스트만 제출");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("sort", "createdAt,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].attachments.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].attachments[0].originalName").value("a.txt"))
                .andExpect(jsonPath("$.data.content[1].attachments.length()").value(0));
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
    void invalidSortFieldStillReturnsServerErrorUnaffectedByPropertyReferenceExceptionFix() throws Exception {
        Long studentId = insertMember("student08", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("sort", "bogusField,asc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void sortByUpdatedAtDescendingOrdersResultsNewestFirst() throws Exception {
        Long studentId = insertMember("student06", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long assignmentOldId = insertAssignment("오래된과제");
        Long assignmentNewId = insertAssignment("최신과제");
        submitText(studentId, token, assignmentOldId, "오래된 제출");
        submitText(studentId, token, assignmentNewId, "최신 제출");
        jdbcTemplate.update("UPDATE submission SET updated_at = now() - interval '1 day' "
                + "WHERE assignment_id = ?", assignmentOldId);
        jdbcTemplate.update("UPDATE submission SET updated_at = now() WHERE assignment_id = ?", assignmentNewId);

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("sort", "updatedAt,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].textContent").value("최신 제출"))
                .andExpect(jsonPath("$.data.content[1].textContent").value("오래된 제출"));
    }

    @Test
    void listingWithAttachmentsDoesNotTriggerNPlusOneQueries() throws Exception {
        Long studentId = insertMember("student07", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        for (int i = 0; i < 5; i++) {
            MockMultipartFile file = new MockMultipartFile("files", "f" + i + ".txt", "text/plain", "x".getBytes());
            mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", insertAssignment("과제" + i))
                            .file(jsonPart("제출" + i)).file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(5));

        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(4);
    }

    private void submitText(Long studentId, String token, Long assignmentId, String textContent) throws Exception {
        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .file(jsonPart(textContent))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private MockMultipartFile jsonPart(String textContent) {
        String body = textContent == null ? "{}" : "{\"textContent\":\"" + textContent + "\"}";
        return new MockMultipartFile("request", "request", "application/json", body.getBytes());
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