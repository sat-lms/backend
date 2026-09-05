package com.sat.lms.attachment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.repository.AssignmentAttachmentRepository;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.S3FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.repository.NoticeRepository;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("s3-integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
@AutoConfigureMockMvc
@Import(AttachmentDomainS3PostgreSqlIntegrationTest.TrackingStorageConfiguration.class)
class AttachmentDomainS3PostgreSqlIntegrationTest {

    private static final List<String> REQUIRED_ENVIRONMENT = List.of(
            "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_REGION",
            "AWS_S3_BUCKET", "AWS_S3_PRESIGNED_EXPIRATION_MINUTES");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lms_test").withUsername("lms_test").withPassword("lms_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeAll
    static void requireAwsEnvironment() {
        Assumptions.assumeTrue(REQUIRED_ENVIRONMENT.stream().allMatch(name -> {
            String value = System.getenv(name);
            return value != null && !value.isBlank();
        }), "Required AWS environment variables are not exported to the test process");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MemberRepository memberRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired NoticeAttachmentRepository noticeAttachmentRepository;
    @Autowired AssignmentAttachmentRepository assignmentAttachmentRepository;
    @Autowired SubmissionAttachmentRepository submissionAttachmentRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired TrackingFileStorage trackingStorage;
    @Autowired S3Client s3Client;

    @AfterEach
    void removeOnlyObjectsCreatedByThisTest() {
        trackingStorage.deleteAllTracked();
        for (String key : trackingStorage.trackedKeys()) {
            assertMissing(key);
        }
    }

    @Test
    void noticeAssignmentAndSubmissionFlowsPersistDownloadAndDeleteRealS3Objects() throws Exception {
        String run = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Member admin = insertAdmin("8" + run.substring(0, 7));
        Member student = saveStudent("7" + run.substring(0, 7), MemberStatus.APPROVED);
        Member other = saveStudent("6" + run.substring(0, 7), MemberStatus.APPROVED);
        Member rejected = saveStudent("5" + run.substring(0, 7), MemberStatus.REJECTED);
        String adminToken = token(admin);
        String studentToken = token(student);
        String otherToken = token(other);
        String rejectedToken = token(rejected);

        long noticeId = createNotice(run, adminToken);
        Notice notice = noticeRepository.findById(noticeId).orElseThrow();
        byte[] noticeBytes = bytes("notice-" + run);
        long noticeAttachmentId = uploadAttachment(
                "/api/v1/notices/" + notice.getId() + "/attachments", "notice.pdf", noticeBytes, adminToken);
        Attachment noticeFile = attachmentRepository.findById(noticeAttachmentId).orElseThrow();
        assertMetadata(noticeFile, "notice.pdf", "pdf", noticeBytes.length);
        assertThat(noticeFile.getStorageKey()).startsWith("notices/" + notice.getId() + "/");
        assertThat(noticeAttachmentRepository.countByNoticeId(notice.getId())).isEqualTo(1);
        head(noticeFile.getStorageKey());
        assertBucketIsPrivate();
        assertDownload("/api/v1/notice-attachments/" + noticeAttachmentId + "/download-url",
                studentToken, noticeBytes);
        MvcResult noticeDetail = mockMvc.perform(get("/api/v1/notices/{id}", notice.getId())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertFirstOriginalName(noticeDetail, "attachments", "notice.pdf");
        int trackedAfterNotice = trackingStorage.trackedKeys().size();
        mockMvc.perform(multipart("/api/v1/notices/{id}/attachments", notice.getId())
                        .file(new MockMultipartFile("files", "blocked.pdf", "application/pdf", noticeBytes))
                        .header("Authorization", bearer(studentToken))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/notice-attachments/{id}", noticeAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isForbidden());
        assertThat(trackingStorage.trackedKeys()).hasSize(trackedAfterNotice);

        long assignmentId = createAssignment(run, adminToken);
        Assignment assignment = assignmentRepository.findById(assignmentId).orElseThrow();
        byte[] assignmentBytes = bytes("assignment-" + run);
        long assignmentAttachmentId = uploadAttachment(
                "/api/v1/assignments/" + assignment.getId() + "/attachments",
                "assignment.pdf", assignmentBytes, adminToken);
        Attachment assignmentFile = attachmentRepository.findById(assignmentAttachmentId).orElseThrow();
        assertMetadata(assignmentFile, "assignment.pdf", "pdf", assignmentBytes.length);
        assertThat(assignmentFile.getStorageKey()).startsWith("assignments/" + assignment.getId() + "/");
        assertThat(assignmentAttachmentRepository.countByAssignmentId(assignment.getId())).isEqualTo(1);
        head(assignmentFile.getStorageKey());
        assertDownload("/api/v1/assignment-attachments/" + assignmentAttachmentId + "/download-url",
                studentToken, assignmentBytes);
        MvcResult assignmentDetail = mockMvc.perform(get("/api/v1/assignments/{id}", assignment.getId())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertFirstOriginalName(assignmentDetail, "attachments", "assignment.pdf");
        int trackedAfterAssignment = trackingStorage.trackedKeys().size();
        mockMvc.perform(multipart("/api/v1/assignments/{id}/attachments", assignment.getId())
                        .file(new MockMultipartFile("files", "blocked.pdf", "application/pdf", assignmentBytes))
                        .header("Authorization", bearer(studentToken))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/assignment-attachments/{id}", assignmentAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isForbidden());
        assertThat(trackingStorage.trackedKeys()).hasSize(trackedAfterAssignment);

        byte[] firstSubmissionBytes = bytes("submission-first-" + run);
        long firstSubmissionAttachmentId = submit(assignment.getId(), firstSubmissionBytes, studentToken, false);
        Attachment firstSubmissionFile = attachmentRepository.findById(firstSubmissionAttachmentId).orElseThrow();
        assertSubmissionPath(firstSubmissionFile, submissionRepository
                .findByAssignmentIdAndStudentId(assignment.getId(), student.getId()).orElseThrow().getId());
        assertDownload("/api/v1/submission-attachments/" + firstSubmissionAttachmentId + "/download-url",
                studentToken, firstSubmissionBytes);
        MvcResult submissionDetail = mockMvc.perform(get("/api/v1/assignments/{id}/submission", assignment.getId())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertFirstOriginalName(submissionDetail, "files", "submission.txt");
        mockMvc.perform(get("/api/v1/submission-attachments/{id}/download-url", firstSubmissionAttachmentId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/submission-attachments/{id}", firstSubmissionAttachmentId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/notice-attachments/{id}/download-url", assignmentAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/assignment-attachments/{id}/download-url", noticeAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/notice-attachments/{id}/download-url", firstSubmissionAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/assignment-attachments/{id}/download-url", firstSubmissionAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/submission-attachments/{id}/download-url", noticeAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/submission-attachments/{id}/download-url", assignmentAttachmentId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/notice-attachments/{id}/download-url", noticeAttachmentId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notice-attachments/{id}/download-url", noticeAttachmentId)
                        .header("Authorization", bearer(rejectedToken))).andExpect(status().isForbidden());

        byte[] replacementBytes = bytes("submission-replacement-" + run);
        long replacementId = submit(assignment.getId(), replacementBytes, studentToken, true);
        Attachment replacement = attachmentRepository.findById(replacementId).orElseThrow();
        assertMissing(firstSubmissionFile.getStorageKey());
        head(replacement.getStorageKey());

        mockMvc.perform(delete("/api/v1/submission-attachments/{id}", replacementId)
                        .header("Authorization", bearer(studentToken))).andExpect(status().isOk());
        assertThat(attachmentRepository.existsById(replacementId)).isFalse();
        assertMissing(replacement.getStorageKey());

        long finalSubmissionAttachmentId = submit(assignment.getId(), bytes("submission-final-" + run),
                studentToken, true);
        String finalSubmissionKey = attachmentRepository.findById(finalSubmissionAttachmentId).orElseThrow()
                .getStorageKey();
        mockMvc.perform(delete("/api/v1/assignments/{id}/submission", assignment.getId())
                        .header("Authorization", bearer(studentToken))).andExpect(status().isOk());
        assertThat(submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId())).isEmpty();
        assertMissing(finalSubmissionKey);

        mockMvc.perform(delete("/api/v1/notice-attachments/{id}", noticeAttachmentId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/assignment-attachments/{id}", assignmentAttachmentId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        assertThat(attachmentRepository.existsById(noticeAttachmentId)).isFalse();
        assertThat(attachmentRepository.existsById(assignmentAttachmentId)).isFalse();
        assertMissing(noticeFile.getStorageKey());
        assertMissing(assignmentFile.getStorageKey());

    }

    private long uploadAttachment(String path, String name, byte[] content, String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", name, "application/pdf", content);
        MvcResult result = mockMvc.perform(multipart(path).file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode data = successfulData(result);
        assertThat(data.isArray()).isTrue();
        assertThat(data).isNotEmpty();
        return positiveId(data.get(0), "attachmentId");
    }

    private long createNotice(String run, String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/notices")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"s3-notice-" + run
                                + "\",\"content\":\"content\",\"isPinned\":false}"))
                .andExpect(status().isCreated()).andReturn();
        return positiveId(successfulData(result), "noticeId");
    }

    private long createAssignment(String run, String adminToken) throws Exception {
        String dueAt = java.time.LocalDateTime.now().plusDays(1)
                .withNano(0).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/assignments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"s3-assignment-" + run + "\",\"content\":\"content\","
                                + "\"dueAt\":\"" + dueAt + "\",\"allowLateSubmission\":true}"))
                .andExpect(status().isCreated()).andReturn();
        return positiveId(successfulData(result), "assignmentId");
    }

    private long submit(long assignmentId, byte[] content, String token, boolean replace) throws Exception {
        MockMultipartFile request = new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"textContent\":\"kept text\"}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = new MockMultipartFile("files", "submission.txt", "text/plain", content);
        var builder = replace
                ? multipart(HttpMethod.PUT, "/api/v1/assignments/{id}/submission", assignmentId)
                : multipart("/api/v1/assignments/{id}/submission", assignmentId);
        MvcResult result = mockMvc.perform(builder.file(request).file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(replace ? status().isOk() : status().isCreated()).andReturn();
        JsonNode data = successfulData(result);
        positiveId(data, "submissionId");
        JsonNode files = requiredNonEmptyArray(data, "files");
        return positiveId(files.get(0), "attachmentId");
    }

    private void assertDownload(String path, String token, byte[] expected) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = successfulData(result);
        assertThat(data.isObject()).isTrue();
        JsonNode downloadUrl = data.get("downloadUrl");
        assertThat(downloadUrl).as("downloadUrl must be present").isNotNull();
        assertThat(downloadUrl.isTextual()).as("downloadUrl must be textual").isTrue();
        assertThat(downloadUrl.textValue()).as("downloadUrl must not be blank").isNotBlank();
        String url = downloadUrl.textValue();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(expected);
    }

    private void assertBucketIsPrivate() throws Exception {
        String bucket = System.getenv("AWS_S3_BUCKET");
        software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration block;
        try {
            block = s3Client.getPublicAccessBlock(GetPublicAccessBlockRequest.builder().bucket(bucket).build())
                    .publicAccessBlockConfiguration();
        } catch (S3Exception exception) {
            String errorCode = exception.awsErrorDetails() == null
                    ? "unknown"
                    : exception.awsErrorDetails().errorCode();
            throw new AssertionError("Bucket Public Access Block could not be verified"
                    + " (status=" + exception.statusCode() + ", errorCode=" + errorCode + ")");
        }
        Boolean blockPublicAcls = block.blockPublicAcls();
        Boolean ignorePublicAcls = block.ignorePublicAcls();
        Boolean blockPublicPolicy = block.blockPublicPolicy();
        Boolean restrictPublicBuckets = block.restrictPublicBuckets();
        String publicAccessBlockValues = "Public Access Block values:"
                + " blockPublicAcls=" + blockPublicAcls
                + ", ignorePublicAcls=" + ignorePublicAcls
                + ", blockPublicPolicy=" + blockPublicPolicy
                + ", restrictPublicBuckets=" + restrictPublicBuckets;
        assertThat(blockPublicAcls).as(publicAccessBlockValues).isTrue();
        assertThat(ignorePublicAcls).as(publicAccessBlockValues).isTrue();
        assertThat(blockPublicPolicy).as(publicAccessBlockValues).isTrue();
        assertThat(restrictPublicBuckets).as(publicAccessBlockValues).isTrue();
        String region = System.getenv("AWS_REGION");
        String key = trackingStorage.trackedKeys().iterator().next();
        URI publicUri = URI.create("https://s3." + region + ".amazonaws.com/" + bucket + "/" + key);
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(publicUri).GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isNotEqualTo(200);
    }

    private void assertMetadata(Attachment attachment, String name, String extension, int bytes) {
        assertThat(attachment.getOriginalName()).isEqualTo(name);
        assertThat(attachment.getExtension()).isEqualTo(extension);
        assertThat(attachment.getSizeKb()).isEqualTo((bytes + 1023L) / 1024L);
    }

    private void assertSubmissionPath(Attachment attachment, long submissionId) {
        String[] segments = attachment.getStorageKey().split("/");
        assertThat(segments).hasSize(3);
        assertThat(segments[0]).isEqualTo("submissions");
        assertThatCode(() -> UUID.fromString(segments[1])).doesNotThrowAnyException();
        assertThat(segments[1]).isNotEqualTo(Long.toString(submissionId));
        assertThat(segments[2]).isEqualTo(attachment.getStoredName());
    }

    private byte[] bytes(String seed) {
        return seed.repeat(100).getBytes(StandardCharsets.UTF_8);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode successfulData(MvcResult result) throws Exception {
        JsonNode response = json(result);
        JsonNode success = response.get("success");
        assertThat(success).isNotNull();
        assertThat(success.isBoolean()).isTrue();
        assertThat(success.booleanValue()).isTrue();
        JsonNode data = response.get("data");
        assertThat(data).isNotNull();
        return data;
    }

    private long positiveId(JsonNode object, String fieldName) {
        assertThat(object).isNotNull();
        assertThat(object.isObject()).isTrue();
        JsonNode id = object.get(fieldName);
        assertThat(id).as(fieldName + " must be present").isNotNull();
        assertThat(id.isIntegralNumber()).as(fieldName + " must be numeric").isTrue();
        assertThat(id.longValue()).as(fieldName + " must be positive").isPositive();
        return id.longValue();
    }

    private void assertFirstOriginalName(MvcResult result, String arrayField, String expected) throws Exception {
        JsonNode data = successfulData(result);
        assertThat(data.isObject()).isTrue();
        JsonNode items = requiredNonEmptyArray(data, arrayField);
        JsonNode originalName = items.get(0).get("originalName");
        assertThat(originalName).as(arrayField + "[0].originalName must be present").isNotNull();
        assertThat(originalName.isTextual()).as(arrayField + "[0].originalName must be textual").isTrue();
        assertThat(originalName.textValue()).isEqualTo(expected);
    }

    private JsonNode requiredNonEmptyArray(JsonNode object, String fieldName) {
        assertThat(object).isNotNull();
        assertThat(object.isObject()).isTrue();
        JsonNode array = object.get(fieldName);
        assertThat(array).as(fieldName + " must be present").isNotNull();
        assertThat(array.isArray()).as(fieldName + " must be an array").isTrue();
        assertThat(array).as(fieldName + " must not be empty").isNotEmpty();
        return array;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void head(String key) {
        s3Client.headObject(HeadObjectRequest.builder().bucket(System.getenv("AWS_S3_BUCKET")).key(key).build());
    }

    private void assertMissing(String key) {
        try {
            head(key);
            throw new AssertionError("A test-created S3 object still exists after deletion");
        } catch (S3Exception exception) {
            assertThat(exception.statusCode()).isEqualTo(404);
        }
    }

    private String token(Member member) {
        return jwtTokenProvider.createAccessToken(member.getId(), member.getRole().name());
    }

    private Member saveStudent(String number, MemberStatus status) {
        Member member = Member.createStudent(number, "s3-student", passwordEncoder.encode("password1"));
        if (status != MemberStatus.PENDING) member.applyReviewResult(status);
        return memberRepository.saveAndFlush(member);
    }

    private Member insertAdmin(String number) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO member(student_number,name,password_hash,role,status,created_at,updated_at)
                VALUES (?, ?, ?, 'ADMIN', 'APPROVED', now(), now()) RETURNING id
                """, Long.class, number, "s3-admin", passwordEncoder.encode("password1"));
        return memberRepository.findById(id).orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TrackingStorageConfiguration {
        @Bean
        @Primary
        TrackingFileStorage trackingFileStorage(S3FileStorage delegate) {
            return new TrackingFileStorage(delegate);
        }
    }

    static class TrackingFileStorage implements FileStorage {
        private final S3FileStorage delegate;
        private final Set<String> tracked = java.util.Collections.synchronizedSet(new LinkedHashSet<>());

        TrackingFileStorage(S3FileStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public StoredFile upload(org.springframework.web.multipart.MultipartFile file, String directory) {
            StoredFile stored = delegate.upload(file, directory);
            tracked.add(stored.storageKey());
            return stored;
        }

        @Override
        public void delete(String storageKey) {
            delegate.delete(storageKey);
        }

        @Override
        public DownloadUrl createDownloadUrl(String storageKey) {
            return delegate.createDownloadUrl(storageKey);
        }

        Set<String> trackedKeys() {
            synchronized (tracked) {
                return Set.copyOf(tracked);
            }
        }

        void deleteAllTracked() {
            boolean failed = false;
            for (String key : trackedKeys()) {
                try {
                    delegate.delete(key);
                } catch (RuntimeException exception) {
                    failed = true;
                }
            }
            if (failed) throw new IllegalStateException("One or more S3 integration cleanup operations failed");
        }
    }
}
