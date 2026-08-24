package com.sat.lms.notice.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoticeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class NoticeControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean NoticeService noticeService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void adminCanCreateNoticeUsingAuthenticatedId() throws Exception {
        authenticate("admin", 7L, "ADMIN");

        mockMvc.perform(post("/api/v1/notices")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"title\":\"안내\",\"content\":\"내용\",\"isPinned\":true}"))
                .andExpect(status().isCreated());

        verify(noticeService).create(any(), eq(7L));
    }

    @Test
    void studentCannotCreateNotice() throws Exception {
        authenticate("student", 8L, "STUDENT");
        mockMvc.perform(post("/api/v1/notices")
                        .header("Authorization", "Bearer student")
                        .contentType("application/json")
                        .content("{\"title\":\"안내\",\"content\":\"내용\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void authenticatedStudentCanReadList() throws Exception {
        authenticate("student", 8L, "STUDENT");
        when(noticeService.getNotices(eq(8L), eq(false), any())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/notices").header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCreateFieldsReturnBadRequest() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        String[] bodies = {
                "{\"content\":\"내용\"}",
                "{\"title\":\"   \",\"content\":\"내용\"}",
                "{\"title\":\"" + "a".repeat(101) + "\",\"content\":\"내용\"}",
                "{\"title\":\"제목\"}",
                "{\"title\":\"제목\",\"content\":\"   \"}"
        };
        for (String body : bodies) {
            mockMvc.perform(post("/api/v1/notices")
                            .header("Authorization", "Bearer admin")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void createAcceptsOneHundredCharacterTitle() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        mockMvc.perform(post("/api/v1/notices")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"title\":\"" + "a".repeat(100) + "\",\"content\":\"내용\"}"))
                .andExpect(status().isCreated());
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
