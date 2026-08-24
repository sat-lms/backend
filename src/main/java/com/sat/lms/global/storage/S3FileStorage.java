package com.sat.lms.global.storage;

import com.sat.lms.global.config.AwsProperties;
import com.sat.lms.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class S3FileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);
    private static final long BYTES_PER_KB = 1024L;
    private static final Pattern DIRECTORY_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final Pattern STORAGE_KEY_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern EXTENSION = Pattern.compile("[A-Za-z0-9]{1,20}");
    private static final String INVALID_FILE_MESSAGE = "올바르지 않은 파일입니다.";
    private static final String INVALID_PATH_MESSAGE = "올바르지 않은 저장 경로입니다.";
    private static final String STORAGE_FAILURE_MESSAGE = "파일 저장소 처리에 실패했습니다.";
    private static final String STORAGE_CONFIG_MESSAGE = "파일 저장소 설정이 올바르지 않습니다.";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsProperties properties;

    public S3FileStorage(S3Client s3Client, S3Presigner s3Presigner, AwsProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        if (properties.getS3().getPresignedExpirationMinutes() <= 0) {
            throw new IllegalStateException(STORAGE_CONFIG_MESSAGE);
        }
    }

    @Override
    public StoredFile upload(MultipartFile file, String directory) {
        validateFile(file);
        validateDirectory(directory);
        String bucket = requireBucket();
        String originalName = file.getOriginalFilename();
        String extension = extractExtension(originalName);
        String storedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        String storageKey = directory + "/" + storedName;
        long size = file.getSize();

        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentLength(size);
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            request.contentType(file.getContentType());
        }

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(request.build(), RequestBody.fromInputStream(inputStream, size));
            return new StoredFile(originalName, storedName, storageKey, extension, toKilobytes(size));
        } catch (IOException e) {
            log.error("S3 upload input failed: exceptionType={}", e.getClass().getSimpleName());
            throw storageFailure();
        } catch (SdkException e) {
            logSdkFailure("upload", e);
            throw storageFailure();
        }
    }

    @Override
    public void delete(String storageKey) {
        validateStorageKey(storageKey);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(storageKey)
                    .build());
        } catch (SdkException e) {
            logSdkFailure("delete", e);
            throw storageFailure();
        }
    }

    @Override
    public String createDownloadUrl(String storageKey) {
        validateStorageKey(storageKey);
        long expirationMinutes = properties.getS3().getPresignedExpirationMinutes();
        if (expirationMinutes <= 0) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, STORAGE_CONFIG_MESSAGE);
        }
        try {
            GetObjectRequest getObject = GetObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(storageKey)
                    .build();
            GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(getObject)
                    .build();
            return s3Presigner.presignGetObject(request).url().toString();
        } catch (SdkException e) {
            logSdkFailure("presign", e);
            throw storageFailure();
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_FILE_MESSAGE);
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || !name.equals(name.trim()) || name.length() > 255
                || name.equals(".") || name.equals("..") || name.contains("..")
                || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_FILE_MESSAGE);
        }
        extractExtension(name);
    }

    private void validateDirectory(String directory) {
        if (!isSafePath(directory, DIRECTORY_SEGMENT)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_PATH_MESSAGE);
        }
    }

    private void validateStorageKey(String storageKey) {
        if (!isSafePath(storageKey, STORAGE_KEY_SEGMENT)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_PATH_MESSAGE);
        }
    }

    private boolean isSafePath(String path, Pattern segmentPattern) {
        if (path == null || path.isBlank() || !path.equals(path.trim()) || path.startsWith("/")
                || path.endsWith("/") || path.contains("//") || path.contains("\\") || path.contains("..")) {
            return false;
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (!segmentPattern.matcher(segment).matches()) return false;
        }
        return true;
    }

    private String extractExtension(String originalName) {
        int lastDot = originalName.lastIndexOf('.');
        if (lastDot < 1) return "";
        if (lastDot == originalName.length() - 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_FILE_MESSAGE);
        }
        String extension = originalName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSION.matcher(extension).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_FILE_MESSAGE);
        }
        return extension;
    }

    private Long toKilobytes(long bytes) {
        return bytes / BYTES_PER_KB + (bytes % BYTES_PER_KB == 0 ? 0 : 1);
    }

    private String requireBucket() {
        String bucket = properties.getS3().getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, STORAGE_CONFIG_MESSAGE);
        }
        return bucket;
    }

    private BusinessException storageFailure() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, STORAGE_FAILURE_MESSAGE);
    }

    private void logSdkFailure(String operation, SdkException exception) {
        log.error("S3 operation failed: operation={}, exceptionType={}", operation,
                exception.getClass().getSimpleName());
    }
}
