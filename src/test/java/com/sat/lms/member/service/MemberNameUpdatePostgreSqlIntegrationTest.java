package com.sat.lms.member.service;

import com.jayway.jsonpath.JsonPath;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.dto.MemberNameUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest
class MemberNameUpdatePostgreSqlIntegrationTest {
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
    @Autowired MemberService memberService;
    @Autowired JwtTokenProvider tokens;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean FileStorage fileStorage;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM submission_attachment");
        jdbc.update("DELETE FROM submission_comment");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM assignment_attachment");
        jdbc.update("DELETE FROM notice_comment");
        jdbc.update("DELETE FROM notice_attachment");
        jdbc.update("DELETE FROM attachment");
        jdbc.update("DELETE FROM notice_read");
        jdbc.update("DELETE FROM assignment");
        jdbc.update("DELETE FROM notice");
        jdbc.update("DELETE FROM member_review");
        jdbc.update("DELETE FROM member");
    }

    @Test
    void loggedInStudentUpdatesOnlyOwnNameAndKeepsExistingJwtValid() throws Exception {
        Long studentId = member("20269994", "기존 이름", "STUDENT", "APPROVED", "Password123");
        Long otherId = member("20269995", "다른 회원", "STUDENT", "APPROVED", "Password123");
        MemberRow before = row(studentId);
        MemberRow otherBefore = row(otherId);
        String token = login("20269994", "Password123");

        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  변경할 이름  \",\"memberId\":" + otherId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("이름을 변경했습니다."))
                .andExpect(jsonPath("$.data.id").value(studentId))
                .andExpect(jsonPath("$.data.studentNumber").value("20269994"))
                .andExpect(jsonPath("$.data.name").value("변경할 이름"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        MemberRow after = row(studentId);
        assertThat(after.name()).isEqualTo("변경할 이름");
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.studentNumber()).isEqualTo(before.studentNumber());
        assertThat(after.passwordHash()).isEqualTo(before.passwordHash());
        assertThat(after.role()).isEqualTo(before.role());
        assertThat(after.status()).isEqualTo(before.status());
        assertThat(after.deactivationReason()).isEqualTo(before.deactivationReason());
        assertThat(after.tokenVersion()).isEqualTo(before.tokenVersion());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(row(otherId)).isEqualTo(otherBefore);

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("변경할 이름"));
        verifyNoInteractions(fileStorage);
    }

    @Test
    void approvedAdminCanUpdateOwnName() throws Exception {
        Long adminId = member("90009994", "관리자", "ADMIN", "APPROVED", "Password123");
        String token = login("90009994", "Password123");
        mockMvc.perform(patch("/api/v1/members/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Admin Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(adminId))
                .andExpect(jsonPath("$.data.name").value("Admin Name"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void pendingRejectedAndWithdrawnMembersAreForbiddenByGuard() throws Exception {
        String[] statuses = {"PENDING", "REJECTED", "WITHDRAWN"};
        for (int index = 0; index < statuses.length; index++) {
            String status = statuses[index];
            Long id = member("8000000" + index, "기존 이름", "STUDENT", status, "Password123");
            String token = tokenFor(id, "STUDENT");
            mockMvc.perform(patch("/api/v1/members/me").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New Name\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
            assertThat(row(id).name()).isEqualTo("기존 이름");
        }
    }

    @Test
    void transactionRollbackRestoresNameAndUpdatedAt() {
        Long id = member("20269996", "기존 이름", "STUDENT", "APPROVED", "Password123");
        MemberRow before = row(id);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberService.updateName(id, new MemberNameUpdateRequest("임시 이름"));
            status.setRollbackOnly();
        });
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void databaseConstraintFailureRollsBackNameAndUpdatedAt() {
        Long id = member("20269997", "기존 이름", "STUDENT", "APPROVED", "Password123");
        MemberRow before = row(id);
        assertThatThrownBy(() -> memberService.updateName(id,
                new MemberNameUpdateRequest("123456789012345678901"))).isInstanceOf(RuntimeException.class);
        assertThat(row(id)).isEqualTo(before);
    }

    private String login(String studentNumber, String password) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNumber\":\"" + studentNumber + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.accessToken");
    }

    private String tokenFor(Long id, String role) {
        return tokens.createAccessToken(id, role);
    }

    private Long member(String number, String name, String role, String status, String password) {
        return jdbc.queryForObject("""
                INSERT INTO member(student_number,name,password_hash,role,status,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, now() - interval '1 day', now() - interval '1 day') RETURNING id
                """, Long.class, number, name, passwordEncoder.encode(password), role, status);
    }

    private MemberRow row(Long id) {
        return jdbc.queryForObject("SELECT id,student_number,name,password_hash,role,status,deactivation_reason,token_version,created_at,updated_at FROM member WHERE id=?",
                (rs, n) -> new MemberRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8),
                        rs.getObject(9, OffsetDateTime.class), rs.getObject(10, OffsetDateTime.class)), id);
    }

    private record MemberRow(Long id, String studentNumber, String name, String passwordHash, String role,
                             String status, String deactivationReason, long tokenVersion,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
