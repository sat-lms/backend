package com.sat.lms.submission.repository;

import com.sat.lms.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
class SubmissionCommentPostgreSqlIntegrationTest {
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
    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanData() {
        jdbcTemplate.update("DELETE FROM submission_comment");
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
    void ownerCanCreateAndListComments() throws Exception {
        Long studentId = insertMember("student01", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글입니다.\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("댓글입니다."))
                .andExpect(jsonPath("$.data.authorName").value("학생"))
                .andExpect(jsonPath("$.data.authorRole").value("STUDENT"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("댓글입니다."));
    }

    @Test
    void adminCanCreateAndListCommentsOnAnySubmission() throws Exception {
        Long studentId = insertMember("student02", "학생", "STUDENT");
        Long adminId = insertMember("admin02", "관리자", "ADMIN");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"관리자 피드백\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorRole").value("ADMIN"));

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void otherStudentCannotCreateOrListCommentsOnSomeoneElsesSubmission() throws Exception {
        Long studentId = insertMember("student03", "학생", "STUDENT");
        Long otherId = insertMember("student04", "다른학생", "STUDENT");
        String otherToken = jwtTokenProvider.createAccessToken(otherId, "STUDENT");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"몰래 댓글\"}")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingCommentOnMissingSubmissionReturnsNotFound() throws Exception {
        Long studentId = insertMember("student05", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankContentIsRejectedWithBadRequest() throws Exception {
        Long studentId = insertMember("student06", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorCanUpdateOwnComment() throws Exception {
        Long studentId = insertMember("student07", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);
        Long commentId = insertComment(submissionId, studentId, "원본 댓글");

        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정된 댓글\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("수정된 댓글"));
    }

    @Test
    void nonAuthorCannotUpdateComment() throws Exception {
        Long studentId = insertMember("student08", "학생", "STUDENT");
        Long otherId = insertMember("student09", "다른학생", "STUDENT");
        String otherToken = jwtTokenProvider.createAccessToken(otherId, "STUDENT");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);
        Long commentId = insertComment(submissionId, studentId, "원본 댓글");

        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"몰래 수정\"}")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorAndAdminCanDeleteButOthersCannot() throws Exception {
        Long studentId = insertMember("student10", "학생", "STUDENT");
        Long adminId = insertMember("admin10", "관리자", "ADMIN");
        Long otherId = insertMember("student11", "다른학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        String otherToken = jwtTokenProvider.createAccessToken(otherId, "STUDENT");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);
        Long ownComment = insertComment(submissionId, studentId, "댓글1");
        Long anotherComment = insertComment(submissionId, studentId, "댓글2");

        mockMvc.perform(delete("/api/v1/submission-comments/{commentId}", ownComment)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/submission-comments/{commentId}", ownComment)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/submission-comments/{commentId}", anotherComment)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void updatingOrDeletingMissingCommentReturnsNotFound() throws Exception {
        Long studentId = insertMember("student12", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/submission-comments/{commentId}", 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/submissions/{submissionId}/comments", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/submission-comments/{commentId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "댓글 {0}개일 때도 쿼리 수는 2로 고정된다")
    @ValueSource(ints = {1, 5, 10, 20})
    void listingCommentsDoesNotTriggerNPlusOneQueriesRegardlessOfCommentCount(int commentCount) throws Exception {
        Long studentId = insertMember("npo" + commentCount, "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long submissionId = insertSubmission(insertAssignment(studentId), studentId);
        for (int i = 0; i < commentCount; i++) insertComment(submissionId, studentId, "댓글" + i);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // size를 댓글 수와 같게 줘서(이하로 강제) content.size() == pageSize가 되게 한다.
        // Spring Data JPA는 content.size()가 pageSize보다 "엄격히 작을" 때만(=마지막 페이지임을
        // content 크기만으로 확신할 수 있을 때만) count 쿼리를 생략하므로, 정확히 같게 맞추면
        // 댓글 수(1/5/10/20)와 무관하게 매번 count 쿼리도 함께 실행된다.
        mockMvc.perform(get("/api/v1/submissions/{submissionId}/comments", submissionId)
                        .param("size", String.valueOf(commentCount))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(commentCount))
                .andExpect(jsonPath("$.data.totalElements").value(commentCount));

        // memberGuard.requireMember/submissionRepository.findById는 findById(PK get)라
        // Hibernate Statistics의 쿼리 실행 횟수에 잡히지 않는다. 여기서 세는 건 JPQL로 작성된
        // 1) 댓글+작성자 fetch join 조회, 2) count 쿼리 = 총 2건. 작성자 수와 무관하게
        // 고정이어야 N+1이 없다는 뜻이다.
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(2);
    }

    @Test
    void deletingSubmissionCascadesToItsComments() throws Exception {
        // 이슈 #96 검증 요청 6번: V8 마이그레이션의 submission_id FK가 ON DELETE CASCADE로
        // 걸려 있어, 댓글이 달린 제출물을 삭제해도 FK 위반 없이 댓글까지 함께 삭제되는지 확인한다.
        Long studentId = insertMember("student14", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long assignmentId = insertAssignment(studentId);
        Long submissionId = insertSubmission(assignmentId, studentId);
        insertComment(submissionId, studentId, "댓글1");
        insertComment(submissionId, studentId, "댓글2");
        Integer commentCountBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission_comment WHERE submission_id = ?", Integer.class, submissionId);
        assertThat(commentCountBefore).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/assignments/{assignmentId}/submission", assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Integer submissionCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission WHERE id = ?", Integer.class, submissionId);
        Integer commentCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission_comment WHERE submission_id = ?", Integer.class, submissionId);
        assertThat(submissionCountAfter).isEqualTo(0);
        assertThat(commentCountAfter).isEqualTo(0);
    }

    private Long insertComment(Long submissionId, Long authorId, String content) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission_comment(submission_id, author_id, content, created_at, updated_at)
                VALUES (?, ?, ?, now(), now()) RETURNING id
                """, Long.class, submissionId, authorId, content);
    }

    private Long insertSubmission(Long assignmentId, Long studentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission(assignment_id, student_id, text_content, is_late, created_at, updated_at)
                VALUES (?, ?, '제출 내용', false, now(), now()) RETURNING id
                """, Long.class, assignmentId, studentId);
    }

    private Long insertAssignment(Long studentId) {
        Long adminId = insertMember("adm" + studentId, "과제관리자", "ADMIN");
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment (admin_id, title, content, due_at, allow_late_submission,
                                        created_at, updated_at)
                VALUES (?, '과제', '내용', ?, false, now(), now()) RETURNING id
                """, Long.class, adminId, OffsetDateTime.now().plusDays(1));
    }

    private Long insertMember(String studentNumber, String name, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'APPROVED', now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role);
    }
}
