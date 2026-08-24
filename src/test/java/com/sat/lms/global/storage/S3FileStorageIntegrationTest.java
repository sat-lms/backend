package com.sat.lms.global.storage;

import com.sat.lms.global.config.AwsProperties;
import com.sat.lms.global.config.AwsS3Config;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("s3-integration")
class S3FileStorageIntegrationTest {
    private static final List<String> REQUIRED_ENVIRONMENT = List.of(
            "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_REGION",
            "AWS_S3_BUCKET", "AWS_S3_PRESIGNED_EXPIRATION_MINUTES");

    @Test
    void uploadsDownloadsAndDeletesPrivateObject() throws Exception {
        Assumptions.assumeTrue(REQUIRED_ENVIRONMENT.stream()
                .allMatch(name -> System.getenv(name) != null && !System.getenv(name).isBlank()),
                "Required AWS environment variables are not exported to the test process");

        AwsProperties properties = new AwsProperties();
        properties.setRegion(System.getenv("AWS_REGION"));
        properties.getS3().setBucket(System.getenv("AWS_S3_BUCKET"));
        properties.getS3().setPresignedExpirationMinutes(
                Long.parseLong(System.getenv("AWS_S3_PRESIGNED_EXPIRATION_MINUTES")));
        AwsS3Config config = new AwsS3Config();
        String directory = "integration-tests/" + UUID.randomUUID();
        byte[] content = "sat-lms-s3-integration".getBytes(StandardCharsets.UTF_8);
        StoredFile stored = null;

        try (S3Client client = config.s3Client(properties);
             S3Presigner presigner = config.s3Presigner(properties)) {
            S3FileStorage storage = new S3FileStorage(client, presigner, properties);
            try {
                stored = storage.upload(new MockMultipartFile(
                        "file", "test.txt", "text/plain", content), directory);
                String downloadUrl = storage.createDownloadUrl(stored.storageKey());
                HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(content);

                storage.delete(stored.storageKey());
                String deletedKey = stored.storageKey();
                assertThatThrownBy(() -> client.headObject(HeadObjectRequest.builder()
                        .bucket(properties.getS3().getBucket()).key(deletedKey).build()))
                        .isInstanceOfSatisfying(S3Exception.class,
                                exception -> assertThat(exception.statusCode()).isEqualTo(404));
                stored = null;
            } finally {
                if (stored != null) storage.delete(stored.storageKey());
            }
        }
    }
}
