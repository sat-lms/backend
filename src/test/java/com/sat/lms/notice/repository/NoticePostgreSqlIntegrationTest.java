package com.sat.lms.notice.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.admin.service.MemberReviewService;
import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.attachment.entity.AssignmentAttachment;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.NoticeAttachment;
import com.sat.lms.attachment.entity.SubmissionAttachment;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.notice.dto.NoticeCreateRequest;
import com.sat.lms.notice.dto.NoticeUpdateRequest;
import com.sat.lms.notice.dto.NoticeListResponse;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.entity.NoticeRead;
import com.sat.lms.notice.service.NoticeService;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.submission.entity.Submission;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.endsWith;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes"
})
class NoticePostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lms_test")
            .withUsername("lms_test")
            .withPassword("lms_test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MemberRepository memberRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired NoticeReadRepository noticeReadRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired AuthService authService;
    @Autowired MemberReviewService memberReviewService;
    @Autowired NoticeService noticeService;
    @Autowired ObjectMapper objectMapper;
    @Autowired Environment environment;
    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockitoBean FileStorage fileStorage;

    @BeforeEach
    void cleanData() {
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

    @Test
    void flywayAppliesV1ThroughV4AndCreatesAllTables() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .map(Object::toString)
                .toList();
        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5", "6", "7");

        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('member', 'member_review', 'notice', 'notice_read',
                                     'assignment', 'attachment', 'notice_attachment',
                                     'assignment_attachment', 'submission', 'submission_attachment')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(10);
    }

    @Test
    @Transactional
    void jpaPersistsNoticeAndNoticeReadWithRelationshipsAndTimestamptz() {
        Long adminId = insertMember("admin01", "관리자", "ADMIN");
        Long studentId = insertMember("student01", "학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Member student = memberRepository.findById(studentId).orElseThrow();
        OffsetDateTime readAt = OffsetDateTime.of(2026, 8, 23, 12, 34, 56, 0,
                ZoneOffset.ofHours(9));

        Notice notice = noticeRepository.saveAndFlush(
                Notice.create(admin, "공지", "내용", true));
        NoticeRead noticeRead = noticeReadRepository.saveAndFlush(
                NoticeRead.create(notice, student, readAt));

        Notice foundNotice = noticeRepository.findWithAdminById(notice.getId()).orElseThrow();
        NoticeRead foundRead = noticeReadRepository.findById(noticeRead.getId()).orElseThrow();
        assertThat(foundNotice.getAdmin().getId()).isEqualTo(adminId);
        assertThat(foundRead.getNotice().getId()).isEqualTo(notice.getId());
        assertThat(foundRead.getMember().getId()).isEqualTo(studentId);
        assertThat(foundNotice.getCreatedAt()).isNotNull();
        assertThat(foundNotice.getUpdatedAt()).isNotNull();
        assertThat(foundRead.getReadAt().toInstant()).isEqualTo(readAt.toInstant());
        assertThat(noticeReadRepository.existsByNoticeIdAndMemberId(notice.getId(), studentId)).isTrue();
        assertThat(noticeReadRepository.existsByNoticeIdAndMemberId(notice.getId(), adminId)).isFalse();
    }

    @Test
    @Transactional
    void noticePageQueryExecutesJoinReadFlagSortingFilteringPagingAndCount() {
        Long adminId = insertMember("admin02", "공지관리자", "ADMIN");
        Long studentId = insertMember("student02", "조회학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        OffsetDateTime base = OffsetDateTime.parse("2026-08-23T00:00:00Z");

        Long pinnedOldId = insertNotice(adminId, "고정-과거", true, base);
        Long pinnedNewId = insertNotice(adminId, "고정-최신", true, base.plusHours(1));
        Long normalOldId = insertNotice(adminId, "일반-과거", false, base.plusHours(2));
        Long normalNewId = insertNotice(adminId, "일반-최신", false, base.plusHours(3));
        noticeReadRepository.insertIfAbsent(pinnedNewId, studentId, base.plusHours(4));

        Page<NoticeListResponse> first = noticeRepository.findNoticePage(studentId, false, PageRequest.of(0, 2));
        Page<NoticeListResponse> second = noticeRepository.findNoticePage(studentId, false, PageRequest.of(1, 2));
        Page<NoticeListResponse> unread = noticeRepository.findNoticePage(studentId, true, PageRequest.of(0, 10));

        assertThat(first.getContent()).extracting(NoticeListResponse::getTitle)
                .containsExactly("고정-최신", "고정-과거");
        assertThat(second.getContent()).extracting(NoticeListResponse::getTitle)
                .containsExactly("일반-최신", "일반-과거");
        assertThat(first.getContent()).extracting(NoticeListResponse::getAuthorName)
                .containsOnly("공지관리자");
        assertThat(first.getContent()).extracting(NoticeListResponse::getIsRead)
                .containsExactly(true, false);
        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getTotalElements()).isEqualTo(4);
        assertThat(unread.getTotalElements()).isEqualTo(3);
        assertThat(unread.getContent()).extracting(NoticeListResponse::getNoticeId)
                .doesNotContain(pinnedNewId)
                .contains(pinnedOldId, normalNewId, normalOldId);
        assertThat(noticeRepository.countUnreadByMemberId(studentId)).isEqualTo(3);
    }

    @Test
    void nativeInsertIsIdempotentAndSafeUnderConcurrentTransactions() throws Exception {
        Long adminId = insertMember("admin03", "관리자", "ADMIN");
        Long studentId = insertMember("student03", "학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Notice notice = noticeRepository.save(Notice.create(admin, "동시성", "내용", false));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        int first = transaction.execute(status -> noticeReadRepository.insertIfAbsent(
                notice.getId(), studentId, OffsetDateTime.now()));
        int repeated = transaction.execute(status -> noticeReadRepository.insertIfAbsent(
                notice.getId(), studentId, OffsetDateTime.now()));
        assertThat(first).isEqualTo(1);
        assertThat(repeated).isZero();

        Long anotherStudentId = insertMember("student04", "동시학생", "STUDENT");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> left = executor.submit(() -> {
                start.await();
                return transaction.execute(status -> noticeReadRepository.insertIfAbsent(
                        notice.getId(), anotherStudentId, OffsetDateTime.now()));
            });
            Future<Integer> right = executor.submit(() -> {
                start.await();
                return transaction.execute(status -> noticeReadRepository.insertIfAbsent(
                        notice.getId(), anotherStudentId, OffsetDateTime.now()));
            });
            start.countDown();
            assertThat(List.of(left.get(), right.get())).containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(noticeReadRepository.countByNoticeId(notice.getId())).isEqualTo(2);
    }

    @Test
    void databaseEnforcesForeignKeysUniqueAndNoticeDeleteCascade() {
        Long adminId = insertMember("admin04", "관리자", "ADMIN");
        Long studentId = insertMember("student05", "학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Notice notice = noticeRepository.save(Notice.create(admin, "제약", "내용", false));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO notice (admin_id, title, content, is_pinned, created_at, updated_at)
                VALUES (?, '잘못된 공지', '내용', false, now(), now())
                """, Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO notice_read (notice_id, member_id, read_at) VALUES (?, ?, now())
                """, Long.MAX_VALUE, studentId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO notice_read (notice_id, member_id, read_at) VALUES (?, ?, now())
                """, notice.getId(), Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("INSERT INTO notice_read (notice_id, member_id, read_at) VALUES (?, ?, now())",
                notice.getId(), studentId);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO notice_read (notice_id, member_id, read_at) VALUES (?, ?, now())",
                notice.getId(), studentId)).isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM notice WHERE id = ?", notice.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_read WHERE notice_id = ?", Long.class, notice.getId())).isZero();
    }

    @Test
    void memberDeleteDoesNotCascadeAuthoredNoticeOrReadRecord() {
        Long adminId = insertMember("admin05", "관리자", "ADMIN");
        Long studentId = insertMember("student06", "학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Notice notice = noticeRepository.save(Notice.create(admin, "보존", "내용", false));
        jdbcTemplate.update("INSERT INTO notice_read (notice_id, member_id, read_at) VALUES (?, ?, now())",
                notice.getId(), studentId);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", studentId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice WHERE id = ?", Long.class,
                notice.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice_read WHERE notice_id = ?", Long.class,
                notice.getId())).isEqualTo(1);
    }

    @Test
    void memberSignupReviewAndNoticeCreateUpdateUseAuditing() throws Exception {
        Long reviewerId = insertMember("admin06", "심사관리자", "ADMIN");
        var signup = authService.signup(new SignupRequest("2026123401", "가입학생", "password1", "password1"));
        Member pending = memberRepository.findById(signup.getMemberId()).orElseThrow();
        OffsetDateTime memberCreatedAt = pending.getCreatedAt();
        OffsetDateTime memberUpdatedAt = pending.getUpdatedAt();
        assertThat(memberCreatedAt).isNotNull().isEqualTo(memberUpdatedAt);

        MemberReviewRequest reviewRequest = objectMapper.readValue("{\"action\":\"APPROVED\"}",
                MemberReviewRequest.class);
        memberReviewService.review(pending.getId(), reviewRequest, reviewerId);
        Member approved = memberRepository.findById(pending.getId()).orElseThrow();
        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");
        assertThat(approved.getCreatedAt().toInstant()).isEqualTo(memberCreatedAt.toInstant());
        assertThat(approved.getUpdatedAt().toInstant()).isAfter(memberUpdatedAt.toInstant());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member_review WHERE member_id = ?",
                Long.class, pending.getId())).isEqualTo(1);

        NoticeCreateRequest createRequest = objectMapper.readValue(
                "{\"title\":\"감사 공지\",\"content\":\"내용\",\"isPinned\":false}", NoticeCreateRequest.class);
        var created = noticeService.create(createRequest, reviewerId);
        Notice createdNotice = noticeRepository.findById(created.getNoticeId()).orElseThrow();
        OffsetDateTime noticeCreatedAt = createdNotice.getCreatedAt();
        OffsetDateTime noticeUpdatedAt = createdNotice.getUpdatedAt();
        assertThat(noticeCreatedAt).isNotNull().isEqualTo(noticeUpdatedAt);

        NoticeUpdateRequest updateRequest = new NoticeUpdateRequest();
        updateRequest.setTitle("수정 공지");
        var updated = noticeService.update(createdNotice.getId(), updateRequest, reviewerId);
        assertThat(updated.getTitle()).isEqualTo("수정 공지");
        assertThat(updated.getCreatedAt().toInstant()).isEqualTo(noticeCreatedAt.toInstant());
        assertThat(updated.getUpdatedAt().toInstant()).isAfter(noticeUpdatedAt.toInstant());
    }

    @Test
    @Transactional
    void persistsCompleteAssignmentAttachmentSubmissionGraphAndAuditsChanges() {
        Long adminId = insertMember("admin07", "과제관리자", "ADMIN");
        Long studentId = insertMember("student07", "제출학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Member student = memberRepository.findById(studentId).orElseThrow();
        OffsetDateTime dueAt = OffsetDateTime.of(2026, 9, 1, 23, 59, 0, 0, ZoneOffset.ofHours(9));

        Assignment assignment = Assignment.create(admin, "과제", "과제 내용", dueAt, true);
        Attachment attachment = Attachment.create("원본.pdf", "stored.pdf", "assignment/stored.pdf", "pdf", 512L);
        entityManager.persist(assignment);
        entityManager.persist(attachment);
        entityManager.flush();
        entityManager.persist(AssignmentAttachment.create(assignment, attachment));

        Notice notice = Notice.create(admin, "첨부 공지", "내용", false);
        entityManager.persist(notice);
        entityManager.persist(NoticeAttachment.create(notice, attachment));

        Submission submission = Submission.create(assignment, student, "제출 내용", false);
        entityManager.persist(submission);
        entityManager.flush();
        entityManager.persist(SubmissionAttachment.create(submission, attachment));
        entityManager.flush();

        assertThat(assignment.getCreatedAt()).isNotNull().isEqualTo(assignment.getUpdatedAt());
        assertThat(attachment.getCreatedAt()).isNotNull();
        assertThat(submission.getCreatedAt()).isNotNull().isEqualTo(submission.getUpdatedAt());
        assertThat(assignment.getDueAt().toInstant()).isEqualTo(dueAt.toInstant());
        OffsetDateTime assignmentCreatedAt = assignment.getCreatedAt();
        OffsetDateTime assignmentUpdatedAt = assignment.getUpdatedAt();
        OffsetDateTime submissionCreatedAt = submission.getCreatedAt();
        OffsetDateTime submissionUpdatedAt = submission.getUpdatedAt();

        assignment.update("수정 과제", null, null, null);
        submission.updateTextContent("수정 제출 내용");
        entityManager.flush();

        assertThat(assignment.getCreatedAt()).isEqualTo(assignmentCreatedAt);
        assertThat(assignment.getUpdatedAt()).isAfter(assignmentUpdatedAt);
        assertThat(submission.getCreatedAt()).isEqualTo(submissionCreatedAt);
        assertThat(submission.getUpdatedAt()).isAfter(submissionUpdatedAt);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment_attachment", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice_attachment", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission_attachment", Long.class)).isEqualTo(1);
    }

    @Test
    void databaseEnforcesNewUniqueForeignKeysIndexesAndDeletePolicies() {
        Long adminId = insertMember("admin08", "관리자", "ADMIN");
        Long studentId = insertMember("student08", "학생", "STUDENT");
        Long assignmentId = insertAssignment(adminId, "과제");
        Long attachmentId = insertAttachment("unique/key");
        Long submissionId = insertSubmission(assignmentId, studentId);
        Long noticeId = insertNotice(adminId, "삭제 공지", false, OffsetDateTime.now(ZoneOffset.UTC));

        jdbcTemplate.update("INSERT INTO assignment_attachment (assignment_id, attachment_id) VALUES (?, ?)",
                assignmentId, attachmentId);
        jdbcTemplate.update("INSERT INTO submission_attachment (submission_id, attachment_id) VALUES (?, ?)",
                submissionId, attachmentId);
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, attachmentId);

        assertThatThrownBy(() -> insertAttachment("unique/key"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO assignment_attachment (assignment_id, attachment_id) VALUES (?, ?)",
                assignmentId, attachmentId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO submission_attachment (submission_id, attachment_id) VALUES (?, ?)",
                submissionId, attachmentId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, attachmentId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSubmission(assignmentId, studentId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAssignment(Long.MAX_VALUE, "잘못된 과제"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSubmission(Long.MAX_VALUE, studentId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM member WHERE id = ?", studentId))
                .isInstanceOf(DataIntegrityViolationException.class);

        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes WHERE schemaname = 'public'
                  AND tablename IN ('assignment', 'attachment', 'notice_attachment',
                                    'assignment_attachment', 'submission', 'submission_attachment')
                """, String.class);
        assertThat(indexes).contains(
                "idx_assignment_admin_id", "idx_assignment_due_at", "idx_assignment_created_at",
                "uk_attachment_storage_key",
                "idx_notice_attachment_notice_id", "idx_notice_attachment_attachment_id",
                "idx_assignment_attachment_assignment_id", "idx_assignment_attachment_attachment_id",
                "uk_submission_assignment_student", "idx_submission_assignment_id",
                "idx_submission_student_id", "idx_submission_created_at",
                "idx_submission_attachment_submission_id", "idx_submission_attachment_attachment_id");

        jdbcTemplate.update("DELETE FROM assignment WHERE id = ?", assignmentId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment_attachment", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM submission_attachment", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?", Long.class,
                attachmentId)).isEqualTo(1);

        jdbcTemplate.update("DELETE FROM notice WHERE id = ?", noticeId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice_attachment", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?", Long.class,
                attachmentId)).isEqualTo(1);

        Long secondNoticeId = insertNotice(adminId, "첨부 삭제 공지", false, OffsetDateTime.now(ZoneOffset.UTC));
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                secondNoticeId, attachmentId);
        jdbcTemplate.update("DELETE FROM attachment WHERE id = ?", attachmentId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice_attachment", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice WHERE id = ?", Long.class,
                secondNoticeId)).isEqualTo(1);
    }

    @Test
    void openInViewIsDisabledForIntegrationRuntime() {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }

    @Test
    void noticeDetailApiWorksWithOpenInViewDisabled() throws Exception {
        Long adminId = insertMember("admin09", "API관리자", "ADMIN");
        Long studentId = insertMember("student09", "API학생", "STUDENT");
        Long noticeId = insertNotice(adminId, "API 공지", false, OffsetDateTime.now(ZoneOffset.UTC));
        String token = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/notices/{noticeId}", noticeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeId").value(noticeId))
                .andExpect(jsonPath("$.data.authorName").value("API관리자"))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.createdAt", endsWith("Z")))
                .andExpect(jsonPath("$.data.updatedAt", endsWith("Z")));
        assertThat(noticeReadRepository.existsByNoticeIdAndMemberId(noticeId, studentId)).isTrue();
    }

    @Test
    void noticeAttachmentUploadPersistsMetadataAndLinksAndSupportsDownload() throws Exception {
        Long adminId = insertMember("admin10", "첨부관리자", "ADMIN");
        Long studentId = insertMember("student10", "다운학생", "STUDENT");
        Long noticeId = insertNotice(adminId, "첨부 공지", false, OffsetDateTime.now(ZoneOffset.UTC));
        MockMultipartFile first = multipartFile("안내.PDF", "첫 파일");
        MockMultipartFile second = multipartFile("서식.HWPX", "두 번째 파일");
        String firstStored = "11111111-1111-1111-1111-111111111111.pdf";
        String secondStored = "22222222-2222-2222-2222-222222222222.hwpx";
        when(fileStorage.upload(any(), eq("notices/" + noticeId))).thenReturn(
                new StoredFile("안내.PDF", firstStored, "notices/" + noticeId + "/" + firstStored, "pdf", 1L),
                new StoredFile("서식.HWPX", secondStored, "notices/" + noticeId + "/" + secondStored, "hwpx", 1L));

        mockMvc.perform(multipart("/api/v1/notices/{noticeId}/attachments", noticeId)
                        .file(first).file(second)
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(adminId, "ADMIN"))
                        .characterEncoding(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].originalName").value("안내.PDF"))
                .andExpect(jsonPath("$.data[0].extension").value("pdf"))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].storedName").doesNotExist());

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, original_name, stored_name, storage_key, extension, size_kb FROM attachment ORDER BY id");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("original_name")).isEqualTo("안내.PDF");
        assertThat(rows.get(0).get("stored_name")).isEqualTo(firstStored);
        assertThat(rows.get(0).get("storage_key")).isEqualTo("notices/" + noticeId + "/" + firstStored);
        assertThat(rows.get(0).get("extension")).isEqualTo("pdf");
        assertThat(rows.get(0).get("size_kb")).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice_attachment WHERE notice_id = ?",
                Long.class, noticeId)).isEqualTo(2L);

        Long attachmentId = ((Number) rows.get(0).get("id")).longValue();
        when(fileStorage.createDownloadUrl("notices/" + noticeId + "/" + firstStored))
                .thenReturn("https://example.test/signed");
        mockMvc.perform(get("/api/v1/notice-attachments/{attachmentId}/download-url", attachmentId)
                        .header("Authorization", "Bearer "
                                + jwtTokenProvider.createAccessToken(studentId, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://example.test/signed"))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.originalName").value("안내.PDF"));
    }

    @Test
    void uploadDatabaseFailureRollsBackRowsAndCompensatesStorage() throws Exception {
        Long adminId = insertMember("admin11", "실패관리자", "ADMIN");
        Long noticeId = insertNotice(adminId, "실패 공지", false, OffsetDateTime.now(ZoneOffset.UTC));
        String storedName = "33333333-3333-3333-3333-333333333333.pdf";
        String storageKey = "notices/" + noticeId + "/" + storedName;
        when(fileStorage.upload(any(), anyString())).thenReturn(
                new StoredFile("a.pdf", storedName, storageKey, "pdf", 1L),
                new StoredFile("b.pdf", storedName, storageKey, "pdf", 1L));

        mockMvc.perform(multipart("/api/v1/notices/{noticeId}/attachments", noticeId)
                        .file(multipartFile("a.pdf", "a")).file(multipartFile("b.pdf", "b"))
                        .header("Authorization", "Bearer "
                                + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice_attachment", Long.class)).isZero();
        verify(fileStorage, times(2)).delete(storageKey);
    }

    @Test
    void otherDomainAttachmentCannotDownloadAndDeleteRunsAfterCommittedDatabaseRemoval() throws Exception {
        Long adminId = insertMember("admin12", "삭제관리자", "ADMIN");
        Long studentId = insertMember("student12", "학생", "STUDENT");
        Long noticeId = insertNotice(adminId, "삭제 공지", false, OffsetDateTime.now(ZoneOffset.UTC));
        Long noticeAttachmentId = insertAttachment("notices/" + noticeId + "/notice.pdf");
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, noticeAttachmentId);
        Long assignmentId = insertAssignment(adminId, "다른 도메인");
        Long otherAttachmentId = insertAttachment("assignments/" + assignmentId + "/other.pdf");
        jdbcTemplate.update("INSERT INTO assignment_attachment (assignment_id, attachment_id) VALUES (?, ?)",
                assignmentId, otherAttachmentId);
        String studentToken = jwtTokenProvider.createAccessToken(studentId, "STUDENT");

        mockMvc.perform(get("/api/v1/notice-attachments/{id}/download-url", otherAttachmentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());
        verify(fileStorage, never()).createDownloadUrl(anyString());

        mockMvc.perform(delete("/api/v1/notice-attachments/{id}", noticeAttachmentId)
                        .header("Authorization", "Bearer "
                                + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_attachment WHERE attachment_id = ?", Long.class,
                noticeAttachmentId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attachment WHERE id = ?", Long.class, noticeAttachmentId)).isZero();
        verify(fileStorage).delete("notices/" + noticeId + "/notice.pdf");
    }

    @Test
    void deleteDatabaseFailureRollsBackRowsAndKeepsStorageObject() throws Exception {
        Long adminId = insertMember("admin13", "롤백관리자", "ADMIN");
        Long noticeId = insertNotice(adminId, "롤백 공지", false, OffsetDateTime.now(ZoneOffset.UTC));
        String storageKey = "notices/" + noticeId + "/rollback.pdf";
        Long attachmentId = insertAttachment(storageKey);
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, attachmentId);
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_notice_attachment_delete() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced delete failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_notice_attachment_delete
                BEFORE DELETE ON notice_attachment
                FOR EACH ROW EXECUTE FUNCTION fail_notice_attachment_delete()
                """);
        try {
            mockMvc.perform(delete("/api/v1/notice-attachments/{id}", attachmentId)
                            .header("Authorization", "Bearer "
                                    + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                    .andExpect(status().is5xxServerError());

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM notice_attachment WHERE attachment_id = ?", Long.class,
                    attachmentId)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM attachment WHERE id = ?", Long.class, attachmentId)).isEqualTo(1L);
            verify(fileStorage, never()).delete(storageKey);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_notice_attachment_delete ON notice_attachment");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_notice_attachment_delete()");
        }
    }

    @Test
    void cumulativeAttachmentLimitRejectsTwoPlusTwoWithoutStorageOrRows() throws Exception {
        Long adminId = insertMember("admin14", "누적관리자", "ADMIN");
        Long noticeId = insertNotice(adminId, "누적 제한", false, OffsetDateTime.now(ZoneOffset.UTC));
        Long firstId = insertAttachment("notices/" + noticeId + "/existing-a.pdf");
        Long secondId = insertAttachment("notices/" + noticeId + "/existing-b.pdf");
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?), (?, ?)",
                noticeId, firstId, noticeId, secondId);

        mockMvc.perform(multipart("/api/v1/notices/{noticeId}/attachments", noticeId)
                        .file(multipartFile("new-a.pdf", "a")).file(multipartFile("new-b.pdf", "b"))
                        .header("Authorization", "Bearer "
                                + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("공지 첨부파일은 최대 3개까지 등록할 수 있습니다."));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_attachment WHERE notice_id = ?", Long.class, noticeId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isEqualTo(2L);
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void cumulativeAttachmentLimitAllowsTwoPlusOne() throws Exception {
        Long adminId = insertMember("admin15", "누적성공관리자", "ADMIN");
        Long noticeId = insertNotice(adminId, "누적 성공", false, OffsetDateTime.now(ZoneOffset.UTC));
        Long firstId = insertAttachment("notices/" + noticeId + "/existing-a.pdf");
        Long secondId = insertAttachment("notices/" + noticeId + "/existing-b.pdf");
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?), (?, ?)",
                noticeId, firstId, noticeId, secondId);
        String storedName = "44444444-4444-4444-4444-444444444444.pdf";
        when(fileStorage.upload(any(), eq("notices/" + noticeId))).thenReturn(new StoredFile(
                "new.pdf", storedName, "notices/" + noticeId + "/" + storedName, "pdf", 1L));

        mockMvc.perform(multipart("/api/v1/notices/{noticeId}/attachments", noticeId)
                        .file(multipartFile("new.pdf", "new"))
                        .header("Authorization", "Bearer "
                                + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_attachment WHERE notice_id = ?", Long.class, noticeId)).isEqualTo(3L);
    }

    @Test
    void deletingNoticeRemovesUnsharedMetadataAndStorageAfterCommit() throws Exception {
        Long adminId = insertMember("admin16", "공지삭제관리자", "ADMIN");
        Long noticeId = insertNotice(adminId, "첨부 공지 삭제", false, OffsetDateTime.now(ZoneOffset.UTC));
        String firstKey = "notices/" + noticeId + "/a.pdf";
        String secondKey = "notices/" + noticeId + "/b.pdf";
        Long firstId = insertAttachment(firstKey);
        Long secondId = insertAttachment(secondKey);
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?), (?, ?)",
                noticeId, firstId, noticeId, secondId);

        mockMvc.perform(delete("/api/v1/notices/{noticeId}", noticeId)
                        .header("Authorization", "Bearer "
                                + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice WHERE id = ?", Long.class, noticeId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notice_attachment WHERE notice_id = ?", Long.class, noticeId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Long.class)).isZero();
        verify(fileStorage).delete(firstKey);
        verify(fileStorage).delete(secondKey);
    }

    @Test
    void deletingNoticeKeepsAttachmentSharedWithAssignment() throws Exception {
        Long adminId = insertMember("admin17", "공유삭제관리자", "ADMIN");
        Long noticeId = insertNotice(adminId, "공유 첨부 공지", false, OffsetDateTime.now(ZoneOffset.UTC));
        Long assignmentId = insertAssignment(adminId, "공유 과제");
        String sharedKey = "notices/" + noticeId + "/shared.pdf";
        Long attachmentId = insertAttachment(sharedKey);
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, attachmentId);
        jdbcTemplate.update("INSERT INTO assignment_attachment (assignment_id, attachment_id) VALUES (?, ?)",
                assignmentId, attachmentId);

        mockMvc.perform(delete("/api/v1/notices/{noticeId}", noticeId)
                        .header("Authorization", "Bearer "
                                + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?",
                Long.class, attachmentId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM assignment_attachment WHERE attachment_id = ?",
                Long.class, attachmentId)).isEqualTo(1L);
        verify(fileStorage, never()).delete(sharedKey);
    }

    @Test
    void noticeDeleteDatabaseFailureRollsBackAllRowsAndKeepsStorage() throws Exception {
        Long adminId = insertMember("admin18", "전체롤백관리자", "ADMIN");
        Long noticeId = insertNotice(adminId, "전체 삭제 롤백", false, OffsetDateTime.now(ZoneOffset.UTC));
        String storageKey = "notices/" + noticeId + "/rollback-all.pdf";
        Long attachmentId = insertAttachment(storageKey);
        jdbcTemplate.update("INSERT INTO notice_attachment (notice_id, attachment_id) VALUES (?, ?)",
                noticeId, attachmentId);
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_full_notice_attachment_delete() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced full delete failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_full_notice_attachment_delete
                BEFORE DELETE ON notice_attachment
                FOR EACH ROW EXECUTE FUNCTION fail_full_notice_attachment_delete()
                """);
        try {
            mockMvc.perform(delete("/api/v1/notices/{noticeId}", noticeId)
                            .header("Authorization", "Bearer "
                                    + jwtTokenProvider.createAccessToken(adminId, "ADMIN")))
                    .andExpect(status().is5xxServerError());

            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice WHERE id = ?",
                    Long.class, noticeId)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notice_attachment WHERE notice_id = ?",
                    Long.class, noticeId)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment WHERE id = ?",
                    Long.class, attachmentId)).isEqualTo(1L);
            verify(fileStorage, never()).delete(storageKey);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_full_notice_attachment_delete ON notice_attachment");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_full_notice_attachment_delete()");
        }
    }

    private MockMultipartFile multipartFile(String originalName, String content) {
        return new MockMultipartFile("files", originalName, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private Long insertMember(String studentNumber, String name, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'APPROVED', now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role);
    }

    private Long insertNotice(Long adminId, String title, boolean pinned, OffsetDateTime createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notice (admin_id, title, content, is_pinned, created_at, updated_at)
                VALUES (?, ?, '내용', ?, ?, ?) RETURNING id
                """, Long.class, adminId, title, pinned, createdAt, createdAt);
    }

    private Long insertAssignment(Long adminId, String title) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO assignment (admin_id, title, content, due_at, allow_late_submission,
                                        created_at, updated_at)
                VALUES (?, ?, '내용', now() + interval '1 day', false, now(), now()) RETURNING id
                """, Long.class, adminId, title);
    }

    private Long insertAttachment(String storageKey) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO attachment (original_name, stored_name, storage_key, extension, size_kb, created_at)
                VALUES ('original.txt', 'stored.txt', ?, 'txt', 1, now()) RETURNING id
                """, Long.class, storageKey);
    }

    private Long insertSubmission(Long assignmentId, Long studentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission (assignment_id, student_id, text_content, is_late, created_at, updated_at)
                VALUES (?, ?, '내용', false, now(), now()) RETURNING id
                """, Long.class, assignmentId, studentId);
    }
}
