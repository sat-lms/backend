package com.sat.lms.notice.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.notice.dto.NoticeAttachmentDownloadUrlResponse;
import com.sat.lms.notice.service.NoticeAttachmentService;
import io.swagger.v3.oas.annotations.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoticeAttachmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class NoticeAttachmentControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean NoticeAttachmentService noticeAttachmentService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void adminUploadsFilesUsingFilesPartAndGetsCreated() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        MockMultipartFile first = file("한글.pdf");
        MockMultipartFile second = file("image.PNG");
        when(noticeAttachmentService.upload(eq(10L), any(), eq(7L))).thenReturn(List.of());

        mockMvc.perform(multipart("/api/v1/notices/10/attachments")
                        .file(first).file(second).header("Authorization", "Bearer admin")
                        .characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.storedName").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void missingFilesPartReturnsCommonBadRequest() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        when(noticeAttachmentService.upload(eq(10L), eq(null), eq(7L)))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "첨부할 파일을 입력해주세요."));

        mockMvc.perform(multipart("/api/v1/notices/10/attachments")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("첨부할 파일을 입력해주세요."));
    }

    @Test
    void serviceSizeLimitFailuresUseCommonBadRequestResponse() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        when(noticeAttachmentService.upload(eq(10L), any(), eq(7L)))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST,
                        "파일 1개의 용량은 20MB를 초과할 수 없습니다."))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST,
                        "전체 파일 용량은 50MB를 초과할 수 없습니다."));

        mockMvc.perform(multipart("/api/v1/notices/10/attachments")
                        .file(file("large.pdf")).header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("파일 1개의 용량은 20MB를 초과할 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(multipart("/api/v1/notices/10/attachments")
                        .file(file("a.pdf")).file(file("b.pdf"))
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("전체 파일 용량은 50MB를 초과할 수 없습니다."));
    }

    @Test
    void adminCanDeleteAndStudentCannotUploadOrDelete() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        authenticate("student", 8L, "STUDENT");

        mockMvc.perform(delete("/api/v1/notice-attachments/1")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/notices/10/attachments")
                        .file(file("a.pdf")).header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/notice-attachments/1")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAndStudentCanDownload() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        authenticate("student", 8L, "STUDENT");
        when(noticeAttachmentService.getDownloadUrl(1L, 7L))
                .thenReturn(new NoticeAttachmentDownloadUrlResponse("https://example.test/admin", 300, "공지.pdf"));
        when(noticeAttachmentService.getDownloadUrl(1L, 8L))
                .thenReturn(new NoticeAttachmentDownloadUrlResponse("https://example.test/student", 300, "공지.pdf"));

        mockMvc.perform(get("/api/v1/notice-attachments/1/download-url")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("공지.pdf"))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
        mockMvc.perform(get("/api/v1/notice-attachments/1/download-url")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsReturnUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/v1/notices/10/attachments").file(file("a.pdf")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notice-attachments/1/download-url"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/notice-attachments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingResourceUsesCommonErrorResponse() throws Exception {
        authenticate("student", 8L, "STUDENT");
        when(noticeAttachmentService.getDownloadUrl(99L, 8L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 공지 첨부파일입니다."));

        mockMvc.perform(get("/api/v1/notice-attachments/99/download-url")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("존재하지 않는 공지 첨부파일입니다."));
    }

    @Test
    void swaggerDeclaresFilesAsRequiredBinaryArray() throws Exception {
        Method upload = NoticeAttachmentController.class.getMethod("upload",
                Long.class, List.class, Long.class);
        Parameter parameter = upload.getParameters()[1].getAnnotation(Parameter.class);

        assertThat(parameter).isNotNull();
        assertThat(parameter.required()).isTrue();
        assertThat(parameter.array().schema().type()).isEqualTo("string");
        assertThat(parameter.array().schema().format()).isEqualTo("binary");
    }

    private MockMultipartFile file(String originalName) {
        return new MockMultipartFile("files", originalName, "application/octet-stream",
                "파일 내용".getBytes(StandardCharsets.UTF_8));
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
