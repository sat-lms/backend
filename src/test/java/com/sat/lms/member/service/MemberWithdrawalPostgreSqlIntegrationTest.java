package com.sat.lms.member.service;

import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.member.dto.MemberWithdrawalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
class MemberWithdrawalPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lms_test").withUsername("lms_test").withPassword("lms_test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokens;
    @Autowired MemberService memberService;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean FileStorage fileStorage;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM submission_attachment");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM assignment_attachment");
        jdbc.update("DELETE FROM notice_attachment");
        jdbc.update("DELETE FROM attachment");
        jdbc.update("DELETE FROM notice_read");
        jdbc.update("DELETE FROM assignment");
        jdbc.update("DELETE FROM notice");
        jdbc.update("DELETE FROM member_review");
        jdbc.update("DELETE FROM member");
    }

    @Test
    void studentWithdrawalPreservesMemberAndRelatedDataAndBlocksExistingJwt() throws Exception {
        Long adminId = member("90000001", "관리자", "ADMIN", "APPROVED", "Password123");
        Long studentId = member("20230001", "학생", "STUDENT", "APPROVED", "Password123");
        Long noticeId = notice(adminId);
        Long assignmentId = assignment(adminId);
        Long submissionId = submission(assignmentId, studentId);
        Long attachmentId = attachment();
        jdbc.update("INSERT INTO submission_attachment(submission_id, attachment_id) VALUES (?, ?)", submissionId, attachmentId);
        jdbc.update("INSERT INTO notice_read(notice_id, member_id, read_at) VALUES (?, ?, now())", noticeId, studentId);
        jdbc.update("INSERT INTO member_review(member_id, reviewer_id, action, reviewed_at) VALUES (?, ?, 'APPROVED', now())",
                studentId, adminId);
        MemberRow before = row(studentId);
        String token = tokens.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(delete("/api/v1/members/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원탈퇴가 완료되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        MemberRow after = row(studentId);
        assertThat(after.status()).isEqualTo("WITHDRAWN");
        assertThat(after.studentNumber()).isEqualTo(before.studentNumber());
        assertThat(after.name()).isEqualTo(before.name());
        assertThat(after.passwordHash()).isEqualTo(before.passwordHash());
        assertThat(after.role()).isEqualTo(before.role());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(count("member", "id", studentId)).isOne();
        assertThat(count("submission", "id", submissionId)).isOne();
        assertThat(count("attachment", "id", attachmentId)).isOne();
        assertThat(count("submission_attachment", "submission_id", submissionId)).isOne();
        assertThat(count("notice_read", "member_id", studentId)).isOne();
        assertThat(count("member_review", "member_id", studentId)).isOne();

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/members/me/submissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/members/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123\"}"))
                .andExpect(status().isForbidden());

        for (String password : new String[]{"Password123", "WrongPassword1"}) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"studentNumber\":\"20230001\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("학번 또는 비밀번호가 올바르지 않습니다."))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"20230001\",\"name\":\"재가입\","
                                + "\"password\":\"Password123\",\"passwordConfirm\":\"Password123\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(fileStorage);
    }

    @Test
    void wrongPasswordDoesNotChangeAnyMemberField() throws Exception {
        Long id = member("20230002", "학생", "STUDENT", "APPROVED", "Password123");
        MemberRow before = row(id);
        String token = tokens.createAccessToken(id, "STUDENT");
        mockMvc.perform(delete("/api/v1/members/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPassword1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않습니다."));
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void transactionRollbackRestoresStatusAndAuditTimestamp() {
        Long id = member("20230004", "학생", "STUDENT", "APPROVED", "Password123");
        MemberRow before = row(id);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberService.withdraw(id, new MemberWithdrawalRequest("Password123"));
            status.setRollbackOnly();
        });

        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void lastAdminIsProtectedAndAdminCanWithdrawWhenAnotherApprovedAdminExists() throws Exception {
        Long first = member("90000002", "관리자1", "ADMIN", "APPROVED", "Password123");
        Long noticeId = notice(first);
        Long assignmentId = assignment(first);
        String firstToken = tokens.createAccessToken(first, "ADMIN");
        mockMvc.perform(delete("/api/v1/members/me").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123\"}"))
                .andExpect(status().isConflict());
        Long second = member("90000003", "관리자2", "ADMIN", "APPROVED", "Password123");
        mockMvc.perform(delete("/api/v1/members/me").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123\"}"))
                .andExpect(status().isOk());
        assertThat(row(first).status()).isEqualTo("WITHDRAWN");
        assertThat(row(second).status()).isEqualTo("APPROVED");
        assertThat(count("notice", "id", noticeId)).isOne();
        assertThat(count("assignment", "id", assignmentId)).isOne();
    }

    @Test
    void concurrentAdminWithdrawalsLeaveOneApprovedAdmin() throws Exception {
        Long first = member("90000004", "관리자1", "ADMIN", "APPROVED", "Password123");
        Long second = member("90000005", "관리자2", "ADMIN", "APPROVED", "Password123");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> withdrawAfterStart(first, ready, start));
            var two = executor.submit(() -> withdrawAfterStart(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = (one.get(10, TimeUnit.SECONDS) ? 1 : 0) + (two.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE role='ADMIN' AND status='APPROVED'", Integer.class)).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentRequestsForSameMemberAllowOnlyOneWithdrawal() throws Exception {
        Long student = member("20230003", "학생", "STUDENT", "APPROVED", "Password123");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> withdrawAfterStart(student, ready, start));
            var two = executor.submit(() -> withdrawAfterStart(student, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successes = (one.get(10, TimeUnit.SECONDS) ? 1 : 0) + (two.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successes).isOne();
            assertThat(row(student).status()).isEqualTo("WITHDRAWN");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean withdrawAfterStart(Long id, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) return false;
            memberService.withdraw(id, new MemberWithdrawalRequest("Password123"));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private Long member(String number, String name, String role, String status, String password) {
        return jdbc.queryForObject("""
                INSERT INTO member(student_number,name,password_hash,role,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, now() - interval '1 day', now() - interval '1 day') RETURNING id
                """, Long.class, number, name, passwordEncoder.encode(password), role, status);
    }

    private Long notice(Long adminId) {
        return jdbc.queryForObject("INSERT INTO notice(admin_id,title,content,is_pinned,created_at,updated_at) VALUES (?, '공지','내용',false,now(),now()) RETURNING id", Long.class, adminId);
    }

    private Long assignment(Long adminId) {
        return jdbc.queryForObject("INSERT INTO assignment(admin_id,title,content,due_at,allow_late_submission,created_at,updated_at) VALUES (?, '과제','내용',now()+interval '1 day',true,now(),now()) RETURNING id", Long.class, adminId);
    }

    private Long submission(Long assignmentId, Long studentId) {
        return jdbc.queryForObject("INSERT INTO submission(assignment_id,student_id,text_content,is_late,created_at,updated_at) VALUES (?,?,'내용',false,now(),now()) RETURNING id", Long.class, assignmentId, studentId);
    }

    private Long attachment() {
        return jdbc.queryForObject("INSERT INTO attachment(original_name,stored_name,storage_key,extension,size_kb,created_at) VALUES ('a.txt','stored.txt','submissions/test/stored.txt','txt',1,now()) RETURNING id", Long.class);
    }

    private int count(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    private MemberRow row(Long id) {
        return jdbc.queryForObject("SELECT student_number,name,password_hash,role,status,created_at,updated_at FROM member WHERE id=?",
                (rs, n) -> new MemberRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getObject(6, OffsetDateTime.class), rs.getObject(7, OffsetDateTime.class)), id);
    }

    private record MemberRow(String studentNumber, String name, String passwordHash, String role, String status,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
