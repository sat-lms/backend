package com.sat.lms.member.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.service.MemberService;
import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MemberControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MemberService memberService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void validNameUpdateUsesAuthenticatedMemberAndReturnsChangedName() throws Exception {
        authenticate();
        when(memberService.updateName(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(MemberMeResponse.from(Member.createStudent("20269994", "변경할이름", "hash")));

        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"name\":\"변경할이름\",\"memberId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("이름을 변경했습니다."))
                .andExpect(jsonPath("$.data.name").value("변경할이름"));

        verify(memberService).updateName(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.argThat(
                request -> request.getName().equals("변경할이름")));
    }

    @Test
    void validKoreanEnglishAndMiddleSpaceNamesAreAccepted() throws Exception {
        authenticate();
        for (String name : new String[]{"홍길동", "Alice", "Hong Gil Dong"}) {
            mockMvc.perform(patch("/api/v1/members/me")
                            .header("Authorization", "Bearer valid-token")
                            .contentType("application/json")
                            .content("{\"name\":\"" + name + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void invalidOrMissingNameBodyIsBadRequestBeforeService() throws Exception {
        authenticate();
        for (String body : new String[]{"{}", "{\"name\":null}", "{\"name\":\"\"}",
                "{\"name\":\"   \"}", "{\"name\":\"123456789012345678901\"}"}) {
            mockMvc.perform(patch("/api/v1/members/me")
                            .header("Authorization", "Bearer valid-token")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(memberService);
    }

    @Test
    void unauthenticatedNameUpdateIsRejectedBeforeService() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .contentType("application/json").content("{\"name\":\"Alice\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(memberService);
    }

    private void authenticate() {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getMemberId("valid-token")).thenReturn(2L);
        when(tokenProvider.getRole("valid-token")).thenReturn("STUDENT");
    }

    @Test
    void validJwtUsesMemberIdFromToken() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getMemberId("valid-token")).thenReturn(2L);
        when(tokenProvider.getRole("valid-token")).thenReturn("STUDENT");

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        verify(memberService).getMe(2L);
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void validJwtWithdrawsOnlyAuthenticatedMember() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getMemberId("valid-token")).thenReturn(2L);
        when(tokenProvider.getRole("valid-token")).thenReturn("STUDENT");

        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"Password123\",\"memberId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원탈퇴가 완료되었습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(memberService).withdraw(org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unauthenticatedWithdrawalIsRejectedBeforeService() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"Password123\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(memberService);
    }

    @Test
    void invalidWithdrawalPasswordsAreBadRequest() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getMemberId("valid-token")).thenReturn(2L);
        when(tokenProvider.getRole("valid-token")).thenReturn("STUDENT");

        for (String body : new String[]{"{}", "{\"currentPassword\":null}",
                "{\"currentPassword\":\"\"}", "{\"currentPassword\":\"   \"}"}) {
            mockMvc.perform(delete("/api/v1/members/me")
                            .header("Authorization", "Bearer valid-token")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
        verifyNoInteractions(memberService);
    }

    @Test
    void missingWithdrawalBodyIsBadRequestInApiResponseFormat() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getMemberId("valid-token")).thenReturn(2L);
        when(tokenProvider.getRole("valid-token")).thenReturn("STUDENT");

        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(memberService);
    }
}
