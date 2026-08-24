package com.sat.lms.global.storage;

import com.sat.lms.global.config.AwsProperties;
import com.sat.lms.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FileStorageTest {
    S3Client s3Client;
    S3Presigner presigner;
    AwsProperties properties;
    S3FileStorage storage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        presigner = mock(S3Presigner.class);
        properties = properties("test-private-bucket", 7);
        storage = new S3FileStorage(s3Client, presigner, properties);
    }

    @Test
    void uploadUsesPrivateBucketGeneratedKeyAndFileMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "과제.Report.PDF", "application/pdf", new byte[1025]);

        StoredFile stored = storage.upload(file, "assignments/10");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-private-bucket");
        assertThat(request.getValue().key()).isEqualTo(stored.storageKey());
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().contentLength()).isEqualTo(1025L);
        assertThat(stored.originalName()).isEqualTo("과제.Report.PDF");
        assertThat(stored.extension()).isEqualTo("pdf");
        assertThat(stored.storedName()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
        assertThat(stored.storageKey()).isEqualTo("assignments/10/" + stored.storedName());
        assertThat(stored.sizeKb()).isEqualTo(2L);
        assertThat(body.getValue().optionalContentLength()).contains(1025L);
        assertThat(request.getValue().acl()).isNull();
    }

    @Test
    void nonEmptyFileSmallerThanOneKbRoundsUpToOneKb() {
        StoredFile stored = storage.upload(new MockMultipartFile(
                "file", "a.txt", "text/plain", new byte[]{1}), "notices/1");
        assertThat(stored.sizeKb()).isEqualTo(1L);
    }

    @Test
    void exactKilobyteIsNotRoundedFurther() {
        StoredFile stored = storage.upload(new MockMultipartFile(
                "file", "a", null, new byte[1024]), "submissions/1");
        assertThat(stored.sizeKb()).isEqualTo(1L);
        assertThat(stored.extension()).isEmpty();
        assertThat(stored.storedName()).doesNotContain(".");
    }

    @Test
    void emptyFileIsRejected() {
        assertBadRequest(() -> storage.upload(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]), "notices/1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/notices/1", "notices/1/", "notices//1", "notices\\1",
            "notices/../1", "../notices", "C:/notices"})
    void unsafeDirectoryIsRejected(String directory) {
        assertBadRequest(() -> storage.upload(file(), directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.txt", "folder/file.txt", "folder\\file.txt", "file..txt",
            "file.", " file.txt", "file.txt "})
    void unsafeOriginalFilenameIsRejected(String originalName) {
        assertBadRequest(() -> storage.upload(new MockMultipartFile(
                "file", originalName, "text/plain", new byte[]{1}), "notices/1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/notices/key.txt", "notices/key.txt/", "notices//key.txt",
            "notices\\key.txt", "notices/../key.txt"})
    void unsafeStorageKeyIsRejected(String key) {
        assertBadRequest(() -> storage.delete(key));
    }

    @Test
    void deleteUsesConfiguredBucketAndStorageKey() {
        storage.delete("notices/1/file.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-private-bucket");
        assertThat(request.getValue().key()).isEqualTo("notices/1/file.pdf");
    }

    @Test
    void presignedGetUsesConfiguredExpirationWithoutExistenceCall() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://example.invalid/signed").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = storage.createDownloadUrl("submissions/3/file.docx");

        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(url).isEqualTo("https://example.invalid/signed");
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(7));
        GetObjectRequest objectRequest = request.getValue().getObjectRequest();
        assertThat(objectRequest.bucket()).isEqualTo("test-private-bucket");
        assertThat(objectRequest.key()).isEqualTo("submissions/3/file.docx");
    }

    @Test
    void nonPositiveExpirationIsRejectedAsConfigurationError() {
        assertThatThrownBy(() -> new S3FileStorage(s3Client, presigner, properties("bucket", 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("파일 저장소 설정이 올바르지 않습니다.");
    }

    @Test
    void sdkFailureBecomesSafeBusinessExceptionWithoutAwsDetails() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder()
                        .statusCode(500)
                        .message("internal bucket and credential detail")
                        .build());

        assertThatThrownBy(() -> storage.upload(file(), "notices/1"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getMessage())
                            .isEqualTo("파일 저장소 처리에 실패했습니다.")
                            .doesNotContain("bucket", "credential", "internal");
                });
    }

    @Test
    void missingBucketBecomesSafeConfigurationError() {
        S3FileStorage missingBucket = new S3FileStorage(s3Client, presigner, properties("", 5));
        assertThatThrownBy(() -> missingBucket.delete("notices/1/file.txt"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getMessage()).doesNotContain("bucket");
                });
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
    }

    private AwsProperties properties(String bucket, long expirationMinutes) {
        AwsProperties result = new AwsProperties();
        result.getS3().setBucket(bucket);
        result.getS3().setPresignedExpirationMinutes(expirationMinutes);
        return result;
    }

    private void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
