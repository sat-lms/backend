package com.sat.lms.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AuthLoginPostgreSqlIntegrationTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String FAILURE_MESSAGE = "학번 또는 비밀번호가 올바르지 않습니다.";

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoSpyBean JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void resetData() {
        memberRepository.deleteAll();
        clearInvocations(jwtTokenProvider);
    }

    @Test
    void approvedLoginSucceedsAndAllCredentialOrStatusFailuresAreIdenticalWithoutChangingRows() throws Exception {
        Member approved = saveMember("20260001", MemberStatus.APPROVED);
        Member pending = saveMember("20260002", MemberStatus.PENDING);
        Member rejected = saveMember("20260003", MemberStatus.REJECTED);
        Member withdrawn = saveMember("20260004", MemberStatus.WITHDRAWN);
        Map<Long, MemberSnapshot> before = snapshots(approved, pending, rejected, withdrawn);

        mockMvc.perform(login("20260001", "password1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        assertFailure("99999999", "password1");
        assertFailure("20260001", "wrong-password");
        assertFailure("20260002", "password1");
        assertFailure("20260002", "wrong-password");
        assertFailure("20260003", "password1");
        assertFailure("20260003", "wrong-password");
        assertFailure("20260004", "password1");
        assertFailure("20260004", "wrong-password");

        verify(jwtTokenProvider, times(1)).createAccessToken(any(), any());
        memberRepository.flush();
        for (Map.Entry<Long, MemberSnapshot> entry : before.entrySet()) {
            Member current = memberRepository.findById(entry.getKey()).orElseThrow();
            assertThat(MemberSnapshot.from(current)).isEqualTo(entry.getValue());
        }
    }

    private void assertFailure(String studentNumber, String password) throws Exception {
        mockMvc.perform(login(studentNumber, password))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(FAILURE_MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String studentNumber, String password) throws Exception {
        return post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LoginRequest(studentNumber, password)));
    }

    private Member saveMember(String studentNumber, MemberStatus status) {
        Member member = Member.createStudent(studentNumber, "테스트회원", passwordEncoder.encode("password1"));
        if (status != MemberStatus.PENDING) {
            member.applyReviewResult(status);
        }
        return memberRepository.saveAndFlush(member);
    }

    private Map<Long, MemberSnapshot> snapshots(Member... members) {
        Map<Long, MemberSnapshot> result = new LinkedHashMap<>();
        for (Member member : members) {
            Member persisted = memberRepository.findById(member.getId()).orElseThrow();
            result.put(persisted.getId(), MemberSnapshot.from(persisted));
        }
        return result;
    }

    private record MemberSnapshot(MemberStatus status, String passwordHash, OffsetDateTime updatedAt) {
        static MemberSnapshot from(Member member) {
            return new MemberSnapshot(member.getStatus(), member.getPasswordHash(), member.getUpdatedAt());
        }
    }
}
