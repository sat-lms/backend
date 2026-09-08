package com.sat.lms.notice.repository;

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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
class NoticeCommentPostgreSqlIntegrationTest {
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
        jdbcTemplate.update("DELETE FROM notice_comment");
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
    void studentCanCreateAndListComments() throws Exception {
        Long studentId = insertMember("student01", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long noticeId = insertNotice(insertMember("admin01", "관리자", "ADMIN"));

        mockMvc.perform(post("/api/v1/notices/{noticeId}/comments", noticeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글입니다.\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("댓글입니다."))
                .andExpect(jsonPath("$.data.authorName").value("학생"))
                .andExpect(jsonPath("$.data.authorRole").value("STUDENT"));

        mockMvc.perform(get("/api/v1/notices/{noticeId}/comments", noticeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("댓글입니다."));
    }

    @Test
    void adminCanCreateAndListComments() throws Exception {
        Long adminId = insertMember("admin02", "관리자", "ADMIN");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long noticeId = insertNotice(adminId);

        mockMvc.perform(post("/api/v1/notices/{noticeId}/comments", noticeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"관리자 댓글\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorRole").value("ADMIN"));

        mockMvc.perform(get("/api/v1/notices/{noticeId}/comments", noticeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void anyStudentCanCreateAndListCommentsRegardlessOfOwnership() throws Exception {
        // 공지는 애초에 전체 공개라 "본인 것만" 같은 소유권 제약이 없다 — 제출물 댓글(#96)과의
        // 가장 큰 차이점. 이 공지와 아무 관계 없는 학생도 자유롭게 댓글을 달고 볼 수 있어야 한다.
        Long adminId = insertMember("admin03", "관리자", "ADMIN");
        Long noticeId = insertNotice(adminId);
        Long unrelatedStudentId = insertMember("student02", "무관학생", "STUDENT");
        String unrelatedToken = jwtTokenProvider.createAccessToken(unrelatedStudentId, "STUDENT");

        mockMvc.perform(post("/api/v1/notices/{noticeId}/comments", noticeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"아무나 댓글 가능\"}")
                        .header("Authorization", "Bearer " + unrelatedToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notices/{noticeId}/comments", noticeId)
                        .header("Authorization", "Bearer " + unrelatedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void creatingCommentOnMissingNoticeReturnsNotFound() throws Exception {
        Long studentId = insertMember("student03", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(post("/api/v1/notices/{noticeId}/comments", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankContentIsRejectedWithBadRequest() throws Exception {
        Long studentId = insertMember("student04", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long noticeId = insertNotice(insertMember("admin04", "관리자", "ADMIN"));

        mockMvc.perform(post("/api/v1/notices/{noticeId}/comments", noticeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorCanUpdateOwnComment() throws Exception {
        Long studentId = insertMember("student05", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long noticeId = insertNotice(insertMember("admin05", "관리자", "ADMIN"));
        Long commentId = insertComment(noticeId, studentId, "원본 댓글");

        mockMvc.perform(patch("/api/v1/notice-comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정된 댓글\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("수정된 댓글"));
    }

    @Test
    void nonAuthorCannotUpdateComment() throws Exception {
        Long studentId = insertMember("student06", "학생", "STUDENT");
        Long otherId = insertMember("student07", "다른학생", "STUDENT");
        String otherToken = jwtTokenProvider.createAccessToken(otherId, "STUDENT");
        Long noticeId = insertNotice(insertMember("admin06", "관리자", "ADMIN"));
        Long commentId = insertComment(noticeId, studentId, "원본 댓글");

        mockMvc.perform(patch("/api/v1/notice-comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"몰래 수정\"}")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorAndAdminCanDeleteButOthersCannot() throws Exception {
        Long studentId = insertMember("student08", "학생", "STUDENT");
        Long adminId = insertMember("admin07", "관리자", "ADMIN");
        Long otherId = insertMember("student09", "다른학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        String otherToken = jwtTokenProvider.createAccessToken(otherId, "STUDENT");
        Long noticeId = insertNotice(adminId);
        Long ownComment = insertComment(noticeId, studentId, "댓글1");
        Long anotherComment = insertComment(noticeId, studentId, "댓글2");

        mockMvc.perform(delete("/api/v1/notice-comments/{commentId}", ownComment)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/notice-comments/{commentId}", ownComment)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/notice-comments/{commentId}", anotherComment)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void updatingOrDeletingMissingCommentReturnsNotFound() throws Exception {
        Long studentId = insertMember("student10", "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(patch("/api/v1/notice-comments/{commentId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/notice-comments/{commentId}", 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/notices/{noticeId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notices/{noticeId}/comments", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/notice-comments/{commentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/notice-comments/{commentId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "댓글 {0}개일 때도 쿼리 수는 2로 고정된다")
    @ValueSource(ints = {1, 5, 10, 20})
    void listingCommentsDoesNotTriggerNPlusOneQueriesRegardlessOfCommentCount(int commentCount) throws Exception {
        Long studentId = insertMember("npo" + commentCount, "학생", "STUDENT");
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");
        Long noticeId = insertNotice(insertMember("nad" + commentCount, "관리자", "ADMIN"));
        for (int i = 0; i < commentCount; i++) insertComment(noticeId, studentId, "댓글" + i);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // size를 댓글 수와 같게 줘서(이하로 강제) content.size() == pageSize가 되게 한다.
        // Spring Data JPA는 content.size()가 pageSize보다 "엄격히 작을" 때만 count 쿼리를
        // 생략하므로, 정확히 같게 맞추면 댓글 수와 무관하게 매번 count 쿼리도 함께 실행된다.
        mockMvc.perform(get("/api/v1/notices/{noticeId}/comments", noticeId)
                        .param("size", String.valueOf(commentCount))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(commentCount))
                .andExpect(jsonPath("$.data.totalElements").value(commentCount));

        // memberGuard.requireMember/noticeRepository.findById는 findById(PK get)라 Hibernate
        // Statistics의 쿼리 실행 횟수에 잡히지 않는다. 여기서 세는 건 JPQL로 작성된
        // 1) JWT token version 조회, 2) 댓글+작성자 fetch join 조회, 3) count 쿼리 = 총 3건. 작성자 수와 무관하게
        // 고정이어야 N+1이 없다는 뜻이다.
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(3);
    }

    @Test
    void deletingNoticeCascadesToItsComments() throws Exception {
        // 이슈 #96에서 submission 삭제 시 CASCADE를 검증했던 것과 동일한 이유로, 공지 삭제도
        // V9 마이그레이션의 notice_id FK ON DELETE CASCADE가 실제로 동작하는지 확인한다.
        Long adminId = insertMember("admin08", "관리자", "ADMIN");
        String adminToken = jwtTokenProvider.createAccessToken(adminId, "ADMIN");
        Long noticeId = insertNotice(adminId);
        insertComment(noticeId, adminId, "댓글1");
        insertComment(noticeId, adminId, "댓글2");
        Integer commentCountBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_comment WHERE notice_id = ?", Integer.class, noticeId);
        assertThat(commentCountBefore).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/notices/{noticeId}", noticeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Integer noticeCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice WHERE id = ?", Integer.class, noticeId);
        Integer commentCountAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_comment WHERE notice_id = ?", Integer.class, noticeId);
        assertThat(noticeCountAfter).isEqualTo(0);
        assertThat(commentCountAfter).isEqualTo(0);
    }

    private Long insertComment(Long noticeId, Long authorId, String content) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notice_comment(notice_id, author_id, content, created_at, updated_at)
                VALUES (?, ?, ?, now(), now()) RETURNING id
                """, Long.class, noticeId, authorId, content);
    }

    private Long insertNotice(Long adminId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notice (admin_id, title, content, is_pinned, created_at, updated_at)
                VALUES (?, '제목', '내용', false, now(), now()) RETURNING id
                """, Long.class, adminId);
    }

    private Long insertMember(String studentNumber, String name, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'APPROVED', now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role);
    }
}
