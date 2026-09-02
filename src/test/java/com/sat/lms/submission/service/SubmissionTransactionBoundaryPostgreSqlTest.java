package com.sat.lms.submission.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.submission.dto.SubmissionCreateRequest;
import com.sat.lms.notice.service.NoticeAttachmentService;
import com.sat.lms.assignment.service.AssignmentAttachmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=2000",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
class SubmissionTransactionBoundaryPostgreSqlTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lms_boundary_test")
            .withUsername("lms_test")
            .withPassword("lms_test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired SubmissionService submissionService;
    @Autowired NoticeAttachmentService noticeAttachmentService;
    @Autowired AssignmentAttachmentService assignmentAttachmentService;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean FileStorage fileStorage;
    @MockitoBean Clock clock;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(6);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-02T00:00:00Z"));
        jdbcTemplate.update("DELETE FROM submission_attachment");
        jdbcTemplate.update("DELETE FROM submission");
        jdbcTemplate.update("DELETE FROM assignment_attachment");
        jdbcTemplate.update("DELETE FROM notice_attachment");
        jdbcTemplate.update("DELETE FROM attachment");
        jdbcTemplate.update("DELETE FROM assignment");
        jdbcTemplate.update("DELETE FROM notice_read");
        jdbcTemplate.update("DELETE FROM notice");
        jdbcTemplate.update("DELETE FROM member_review");
        jdbcTemplate.update("DELETE FROM member");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void uploadsWaitingBeyondPoolSizeDoNotBlockIndependentDatabaseWork() throws Exception {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        long adminId = insertMember("apool", "ADMIN", "APPROVED");
        long assignmentId = insertAssignment(adminId, now.plusSeconds(3600), false);
        List<Long> students = List.of(
                insertMember("spool1", "STUDENT", "APPROVED"),
                insertMember("spool2", "STUDENT", "APPROVED"),
                insertMember("spool3", "STUDENT", "APPROVED"));
        CountDownLatch uploadsEntered = new CountDownLatch(3);
        CountDownLatch releaseUploads = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            uploadsEntered.countDown();
            if (!releaseUploads.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload release timeout");
            int number = sequence.incrementAndGet();
            String directory = invocation.getArgument(1);
            return new StoredFile("work.txt", "stored-" + number + ".txt",
                    directory + "/stored-" + number + ".txt", "txt", 1L);
        });

        List<Future<?>> uploads = new ArrayList<>();
        try {
            for (Long studentId : students) {
                uploads.add(executor.submit(() -> submissionService.submit(assignmentId, studentId,
                        request(null), List.of(file()))));
            }
            assertThat(uploadsEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> independentQuery = executor.submit(() ->
                    jdbcTemplate.queryForObject("SELECT 1", Integer.class));
            assertThat(independentQuery.get(2, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            releaseUploads.countDown();
        }
        for (Future<?> upload : uploads) upload.get(5, TimeUnit.SECONDS);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Long.class)).isEqualTo(3L);
    }

    @Test
    void deleteRetryRunsAfterCommitWithoutHoldingPoolConnection() throws Exception {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        long adminId = insertMember("adel", "ADMIN", "APPROVED");
        long studentId = insertMember("sdel", "STUDENT", "APPROVED");
        long assignmentId = insertAssignment(adminId, now.plusSeconds(3600), false);
        insertSubmissionWithAttachment(assignmentId, studentId, "submissions/legacy/old.txt");
        CountDownLatch deleteEntered = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            deleteEntered.countDown();
            if (!releaseDelete.await(5, TimeUnit.SECONDS)) throw new AssertionError("delete release timeout");
            if (attempts.incrementAndGet() < 2) throw new RuntimeException("controlled delete failure");
            return null;
        }).when(fileStorage).delete("submissions/legacy/old.txt");

        Future<?> deletion = executor.submit(() -> submissionService.deleteSubmission(assignmentId, studentId));
        try {
            assertThat(deleteEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Long.class)).isZero();
            Future<Integer> independentQuery = executor.submit(() ->
                    jdbcTemplate.queryForObject("SELECT 1", Integer.class));
            assertThat(independentQuery.get(2, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            releaseDelete.countDown();
        }
        deletion.get(5, TimeUnit.SECONDS);
        verify(fileStorage, org.mockito.Mockito.times(2)).delete("submissions/legacy/old.txt");
    }

    @Test
    void memberChangeDuringUploadBlocksSaveAndCompensatesOnlyNewObject() throws Exception {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        long adminId = insertMember("astatus", "ADMIN", "APPROVED");
        long studentId = insertMember("sstatus", "STUDENT", "APPROVED");
        long assignmentId = insertAssignment(adminId, now.plusSeconds(3600), false);
        CountDownLatch uploadEntered = new CountDownLatch(1);
        CountDownLatch releaseUpload = new CountDownLatch(1);
        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            uploadEntered.countDown();
            if (!releaseUpload.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload release timeout");
            String directory = invocation.getArgument(1);
            return new StoredFile("work.txt", "new.txt", directory + "/new.txt", "txt", 1L);
        });

        Future<?> request = executor.submit(() -> submissionService.submit(assignmentId, studentId,
                request(null), List.of(file())));
        assertThat(uploadEntered.await(5, TimeUnit.SECONDS)).isTrue();
        jdbcTemplate.update("UPDATE member SET status='REJECTED', updated_at=now() WHERE id=?", studentId);
        releaseUpload.countDown();
        assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Long.class)).isZero();
        verify(fileStorage).delete(anyString());
    }

    @Test
    void deadlineAndLatePolicyAreSnapshottedBeforeUpload() throws Exception {
        Instant checkedAt = Instant.parse("2026-09-02T00:00:00Z");
        when(clock.instant()).thenReturn(checkedAt, checkedAt.plusSeconds(7200));
        long adminId = insertMember("adead", "ADMIN", "APPROVED");
        long studentId = insertMember("sdead", "STUDENT", "APPROVED");
        long assignmentId = insertAssignment(adminId, checkedAt.plusSeconds(3600), true);
        CountDownLatch uploadEntered = new CountDownLatch(1);
        CountDownLatch releaseUpload = new CountDownLatch(1);
        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            uploadEntered.countDown();
            if (!releaseUpload.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload release timeout");
            String directory = invocation.getArgument(1);
            return new StoredFile("work.txt", "deadline.txt", directory + "/deadline.txt", "txt", 1L);
        });

        Future<?> request = executor.submit(() -> submissionService.submit(assignmentId, studentId,
                request(null), List.of(file())));
        assertThat(uploadEntered.await(5, TimeUnit.SECONDS)).isTrue();
        jdbcTemplate.update("UPDATE assignment SET due_at=?, allow_late_submission=false, updated_at=now() WHERE id=?",
                OffsetDateTime.ofInstant(checkedAt.minusSeconds(1), ZoneOffset.UTC), assignmentId);
        releaseUpload.countDown();
        request.get(5, TimeUnit.SECONDS);
        assertThat(jdbcTemplate.queryForObject("SELECT is_late FROM submission", Boolean.class)).isFalse();
    }

    @Test
    void concurrentFirstSubmissionsKeepOneRowAndCompensateRejectedObject() throws Exception {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        long adminId = insertMember("adup", "ADMIN", "APPROVED");
        long studentId = insertMember("sdup", "STUDENT", "APPROVED");
        long assignmentId = insertAssignment(adminId, now.plusSeconds(3600), false);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        Set<String> uploadedKeys = ConcurrentHashMap.newKeySet();
        Set<String> deletedKeys = ConcurrentHashMap.newKeySet();
        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload release timeout");
            String key = invocation.getArgument(1) + "/concurrent-" + sequence.incrementAndGet() + ".txt";
            uploadedKeys.add(key);
            return new StoredFile("work.txt", key.substring(key.lastIndexOf('/') + 1), key, "txt", 1L);
        });
        doAnswer(invocation -> { deletedKeys.add(invocation.getArgument(0)); return null; })
                .when(fileStorage).delete(anyString());

        List<Future<?>> requests = List.of(
                executor.submit(() -> submissionService.submit(assignmentId, studentId, request(null), List.of(file()))),
                executor.submit(() -> submissionService.submit(assignmentId, studentId, request(null), List.of(file()))));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        int successes = 0;
        int conflicts = 0;
        for (Future<?> future : requests) {
            try { future.get(5, TimeUnit.SECONDS); successes++; }
            catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                conflicts++;
            }
        }
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(1L);
        assertThat(deletedKeys).hasSize(1).isSubsetOf(uploadedKeys);
    }

    @Test
    void concurrentResubmissionsLeaveLatestDbObjectAndDeleteSupersededObjects() throws Exception {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        long adminId = insertMember("aresub", "ADMIN", "APPROVED");
        long studentId = insertMember("sresub", "STUDENT", "APPROVED");
        long assignmentId = insertAssignment(adminId, now.plusSeconds(3600), false);
        insertSubmissionWithAttachment(assignmentId, studentId, "submissions/legacy/old.txt");
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        Set<String> uploadedKeys = ConcurrentHashMap.newKeySet();
        Set<String> deletedKeys = ConcurrentHashMap.newKeySet();
        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload release timeout");
            String key = invocation.getArgument(1) + "/replacement-" + sequence.incrementAndGet() + ".txt";
            uploadedKeys.add(key);
            return new StoredFile("work.txt", key.substring(key.lastIndexOf('/') + 1), key, "txt", 1L);
        });
        doAnswer(invocation -> { deletedKeys.add(invocation.getArgument(0)); return null; })
                .when(fileStorage).delete(anyString());

        List<Future<?>> requests = List.of(
                executor.submit(() -> submissionService.resubmit(assignmentId, studentId, request(null), List.of(file()))),
                executor.submit(() -> submissionService.resubmit(assignmentId, studentId, request(null), List.of(file()))));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        for (Future<?> future : requests) future.get(5, TimeUnit.SECONDS);
        String finalKey = jdbcTemplate.queryForObject("SELECT storage_key FROM attachment", String.class);
        assertThat(uploadedKeys).contains(finalKey);
        assertThat(deletedKeys).contains("submissions/legacy/old.txt");
        assertThat(deletedKeys).containsAll(uploadedKeys.stream().filter(key -> !key.equals(finalKey)).toList());
        assertThat(deletedKeys).doesNotContain(finalKey);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission_attachment", Long.class)).isEqualTo(1L);
    }

    @Test
    void parentDeletedDuringUploadRejectsSaveAndCompensatesNewObject() throws Exception {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        when(clock.instant()).thenReturn(now);
        long adminId = insertMember("aparent", "ADMIN", "APPROVED");
        long studentId = insertMember("sparent", "STUDENT", "APPROVED");
        long assignmentId = insertAssignment(adminId, now.plusSeconds(3600), false);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload release timeout");
            String key = invocation.getArgument(1) + "/orphan.txt";
            return new StoredFile("work.txt", "orphan.txt", key, "txt", 1L);
        });
        Future<?> request = executor.submit(() -> submissionService.submit(assignmentId, studentId,
                request(null), List.of(file())));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        jdbcTemplate.update("DELETE FROM assignment WHERE id=?", assignmentId);
        release.countDown();
        assertThatThrownBy(() -> request.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Long.class)).isZero();
        verify(fileStorage).delete(anyString());
    }

    @Test
    void concurrentNoticeUploadsNeverExceedThreeAndRejectedRequestIsCompensated() throws Exception {
        long adminId = insertMember("anotice", "ADMIN", "APPROVED");
        long noticeId = jdbcTemplate.queryForObject("""
                INSERT INTO notice(admin_id,title,content,is_pinned,created_at,updated_at)
                VALUES (?, 'title', 'content', false, now(), now()) RETURNING id
                """, Long.class, adminId);
        assertConcurrentAttachmentLimit(adminId, noticeId, true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_attachment WHERE notice_id=?", Long.class, noticeId)).isEqualTo(2L);
    }

    @Test
    void concurrentAssignmentUploadsNeverExceedThreeAndRejectedRequestIsCompensated() throws Exception {
        Instant now = Instant.parse("2026-09-02T00:00:00Z");
        long adminId = insertMember("aassign", "ADMIN", "APPROVED");
        long assignmentId = insertAssignment(adminId, now.plusSeconds(3600), false);
        assertConcurrentAttachmentLimit(adminId, assignmentId, false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM assignment_attachment WHERE assignment_id=?", Long.class, assignmentId))
                .isEqualTo(2L);
    }

    private void assertConcurrentAttachmentLimit(long adminId, long parentId, boolean notice) throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        Set<String> uploadedKeys = ConcurrentHashMap.newKeySet();
        Set<String> deletedKeys = ConcurrentHashMap.newKeySet();
        when(fileStorage.upload(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload release timeout");
            String key = invocation.getArgument(1) + "/attachment-" + sequence.incrementAndGet() + ".txt";
            uploadedKeys.add(key);
            return new StoredFile("work.txt", key.substring(key.lastIndexOf('/') + 1), key, "txt", 1L);
        });
        doAnswer(invocation -> { deletedKeys.add(invocation.getArgument(0)); return null; })
                .when(fileStorage).delete(anyString());
        java.util.function.Supplier<Object> upload = notice
                ? () -> noticeAttachmentService.upload(parentId, List.of(file(), file()), adminId)
                : () -> assignmentAttachmentService.upload(parentId, List.of(file(), file()), adminId);
        List<Future<?>> requests = List.of(executor.submit(upload::get), executor.submit(upload::get));
        boolean bothUploadsEntered = entered.await(5, TimeUnit.SECONDS);
        if (!bothUploadsEntered) {
            release.countDown();
            for (Future<?> future : requests) future.get(5, TimeUnit.SECONDS);
        }
        assertThat(bothUploadsEntered).isTrue();
        release.countDown();
        int successes = 0;
        int rejected = 0;
        for (Future<?> future : requests) {
            try { future.get(5, TimeUnit.SECONDS); successes++; }
            catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
                rejected++;
            }
        }
        assertThat(successes).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(deletedKeys).hasSize(2).isSubsetOf(uploadedKeys);
    }

    private SubmissionCreateRequest request(String text) {
        SubmissionCreateRequest request = mock(SubmissionCreateRequest.class);
        when(request.getTextContent()).thenReturn(text);
        return request;
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("files", "work.pdf", "application/pdf", "data".getBytes());
    }

    private long insertMember(String studentNumber, String role, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member(student_number,name,password_hash,role,status,created_at,updated_at)
                VALUES (?, 'test', 'hash', ?, ?, now(), now()) RETURNING id
                """, Long.class, studentNumber, role, status);
    }

    private long insertAssignment(long adminId, Instant dueAt, boolean allowLate) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment(admin_id,title,content,due_at,allow_late_submission,created_at,updated_at)
                VALUES (?, 'title', 'content', ?, ?, now(), now()) RETURNING id
                """, Long.class, adminId, OffsetDateTime.ofInstant(dueAt, ZoneOffset.UTC), allowLate);
    }

    private void insertSubmissionWithAttachment(long assignmentId, long studentId, String storageKey) {
        Long submissionId = jdbcTemplate.queryForObject("""
                INSERT INTO submission(assignment_id,student_id,text_content,is_late,created_at,updated_at)
                VALUES (?, ?, 'text', false, now(), now()) RETURNING id
                """, Long.class, assignmentId, studentId);
        Long attachmentId = jdbcTemplate.queryForObject("""
                INSERT INTO attachment(original_name,stored_name,storage_key,extension,size_kb,created_at)
                VALUES ('old.txt','old.txt',?,'txt',1,now()) RETURNING id
                """, Long.class, storageKey);
        jdbcTemplate.update("INSERT INTO submission_attachment(submission_id,attachment_id) VALUES (?,?)",
                submissionId, attachmentId);
    }
}
