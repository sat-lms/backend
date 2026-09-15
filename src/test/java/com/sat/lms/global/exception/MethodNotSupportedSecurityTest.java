package com.sat.lms.global.exception;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.controller.MemberController;
import com.sat.lms.member.service.MemberService;
import com.sat.lms.notice.controller.NoticeController;
import com.sat.lms.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #116: 경로는 존재하지만 지원하지 않는 HTTP 메서드로 요청했을 때 405가 반환되는지
 * 확인한다. Security 규칙(authenticated / hasRole)을 통과한 뒤 DispatcherServlet
 * 단계에서 나는 문제라, 권한 종류가 다른 두 경로를 각각 검증한다.
 */
@WebMvcTest(controllers = {MemberController.class, NoticeController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MethodNotSupportedSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MemberService memberService;
    @MockitoBean NoticeService noticeService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void unsupportedMethodOnAuthenticatedOnlyPathReturnsMethodNotAllowed() throws Exception {
        // /api/v1/members/me는 SecurityConfig에서 role 제한 없이 authenticated()만 걸려 있다.
        // GET/DELETE만 매핑되어 있고 PATCH는 없다.
        authenticate("student-token", 1L, "STUDENT");

        var result = mockMvc.perform(patch("/api/v1/members/me").header("Authorization", "Bearer student-token"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식입니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        // /api/v1/members/me에는 GET(조회)/DELETE(탈퇴)만 매핑되어 있다 — 실제로 그 목록과
        // 일치하는 Allow 헤더가 오는지 확인한다. Spring이 Allow 값을 콤마로 합치지 않고
        // 같은 이름의 헤더를 여러 개(메서드당 하나씩) 추가하므로 getHeaders()(복수형)로 읽는다.
        assertThat(result.getResponse().getHeaders(HttpHeaders.ALLOW)).contains("GET", "DELETE");
        assertThat(result.getResponse().getHeaders(HttpHeaders.ALLOW)).doesNotContain("PATCH");
    }

    @Test
    void unsupportedMethodOnRoleRestrictedPathReturnsMethodNotAllowedAfterAuthorizationPasses() throws Exception {
        // /api/v1/notices는 POST가 hasRole(ADMIN)으로 걸려 있다. ADMIN 토큰으로 PATCH를 보내면
        // 권한 검사는 통과하지만(=403이 아니어야 함), 컬렉션 루트에는 PATCH 매핑이 없어
        // DispatcherServlet 단계에서 HttpRequestMethodNotSupportedException이 나야 한다.
        authenticate("admin-token", 2L, "ADMIN");

        var result = mockMvc.perform(patch("/api/v1/notices").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식입니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        // /api/v1/notices 컬렉션 루트에는 GET(목록)/POST(등록)만 매핑되어 있다.
        assertThat(result.getResponse().getHeaders(HttpHeaders.ALLOW)).contains("GET", "POST");
        assertThat(result.getResponse().getHeaders(HttpHeaders.ALLOW)).doesNotContain("PATCH");
    }

    @Test
    void nonExistentPathStillReturnsNotFoundRegressionCheck() throws Exception {
        // 이번 변경이 기존 404(NoResourceFoundException) 처리에 영향을 주지 않는지 확인.
        authenticate("student-token", 1L, "STUDENT");

        mockMvc.perform(get("/api/v1/members/does-not-exist").header("Authorization", "Bearer student-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 요청입니다."));
    }

    @Test
    void roleRestrictedPathWithoutPermissionStillReturnsForbiddenBeforeReachingDispatcher() throws Exception {
        // 권한 자체가 없는 경우는 Security가 먼저 막아 403을 내야 하고, 405로 바뀌면 안 된다.
        authenticate("student-token", 1L, "STUDENT");

        mockMvc.perform(patch("/api/v1/notices").header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
