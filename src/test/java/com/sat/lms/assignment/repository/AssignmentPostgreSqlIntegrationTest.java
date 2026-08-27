package com.sat.lms.assignment.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.assignment.service.AssignmentService;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        mockMvc.perform(delete("/api/v1/assignments/{id}", assignmentId)
                        .header("Authorization", "Bearer " + token(adminId, "ADMIN")))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment WHERE id = ?",
                Long.class, assignmentId)).isEqualTo(1L);
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
