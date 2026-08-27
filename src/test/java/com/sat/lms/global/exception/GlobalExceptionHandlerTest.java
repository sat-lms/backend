package com.sat.lms.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.member.entity.Member;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void propertyReferenceExceptionReturnsBadRequest() {
        PropertyReferenceException exception = catchPropertyReferenceException();

        ResponseEntity<ApiResponse<Void>> response = handler.handlePropertyReferenceException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("정렬 기준으로 사용할 수 없는 필드입니다.");
    }

    private PropertyReferenceException catchPropertyReferenceException() {
        try {
            PropertyPath.from("bogusField", Member.class);
        } catch (PropertyReferenceException e) {
            return e;
        }
        throw new IllegalStateException("Expected PropertyReferenceException was not thrown");
    }

    @Test
    void maxUploadSizeExceededReturnsPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("파일 최대 용량을 초과했습니다.");
    }

    @Test
    void dataIntegrityViolationReturnsConflictWithoutCauseMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("secret database details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("중복되거나 제약조건에 위배되는 데이터입니다.")
                .doesNotContain("secret");
    }
}
