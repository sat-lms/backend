package com.sat.lms.assignment.controller;

import com.sat.lms.assignment.service.AssignmentAttachmentService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssignmentAttachmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AssignmentAttachmentControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AssignmentAttachmentService service;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void adminUploadsFilesFieldAndDeletes() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        MockMultipartFile first = file("안내.pdf");
        MockMultipartFile second = file("서식.HWPX");
        when(service.upload(eq(10L), any(), eq(7L))).thenReturn(List.of());

        mockMvc.perform(multipart("/api/v1/assignments/{id}/attachments", 10L)
                        .file(first).file(second).header("Authorization", "Bearer admin")
                        .characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/assignment-attachments/{id}", 1L)
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        verify(service).delete(1L, 7L);
    }

    @Test
    void studentCannotUploadOrDelete() throws Exception {
        authenticate("student", 8L, "STUDENT");
        mockMvc.perform(multipart("/api/v1/assignments/10/attachments").file(file("a.pdf"))
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/assignment-attachments/1")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAndStudentCanDownload() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        authenticate("student", 8L, "STUDENT");
        mockMvc.perform(get("/api/v1/assignment-attachments/1/download-url")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assignment-attachments/1/download-url")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsReturnUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/v1/assignments/10/attachments").file(file("a.pdf")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/assignment-attachments/1/download-url"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/assignment-attachments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingFilesReturnsCommonBadRequest() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        when(service.upload(eq(10L), isNull(), eq(7L)))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, "첨부할 파일을 입력해주세요."));
        mockMvc.perform(multipart("/api/v1/assignments/10/attachments")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void swaggerDeclaresFilesAsRequiredBinaryArray() throws Exception {
        Method upload = AssignmentAttachmentController.class.getMethod("upload",
                Long.class, List.class, Long.class);
        Parameter parameter = upload.getParameters()[1].getAnnotation(Parameter.class);

        assertThat(parameter).isNotNull();
        assertThat(parameter.required()).isTrue();
        assertThat(parameter.array().schema().type()).isEqualTo("string");
        assertThat(parameter.array().schema().format()).isEqualTo("binary");
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/octet-stream",
                "내용".getBytes(StandardCharsets.UTF_8));
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
