package com.sat.lms.notice.repository;

import com.sat.lms.member.entity.Member;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.notice.dto.NoticeListResponse;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.entity.NoticeRead;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
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

    @BeforeEach
    void cleanData() {
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
        assertThat(appliedVersions).containsExactly("1", "2", "3", "4");

        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('member', 'member_review', 'notice', 'notice_read')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(4);
    }

    @Test
    @Transactional
    void jpaPersistsNoticeAndNoticeReadWithRelationshipsAndTimestamptz() {
        Long adminId = insertMember("admin01", "관리자", "ADMIN");
        Long studentId = insertMember("student01", "학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Member student = memberRepository.findById(studentId).orElseThrow();
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 8, 23, 12, 34, 56, 0,
                ZoneOffset.ofHours(9));

        Notice notice = noticeRepository.saveAndFlush(
                Notice.create(admin, "공지", "내용", true, createdAt));
        NoticeRead noticeRead = noticeReadRepository.saveAndFlush(
                NoticeRead.create(notice, student, createdAt.plusMinutes(5)));

        Notice foundNotice = noticeRepository.findWithAdminById(notice.getId()).orElseThrow();
        NoticeRead foundRead = noticeReadRepository.findById(noticeRead.getId()).orElseThrow();
        assertThat(foundNotice.getAdmin().getId()).isEqualTo(adminId);
        assertThat(foundRead.getNotice().getId()).isEqualTo(notice.getId());
        assertThat(foundRead.getMember().getId()).isEqualTo(studentId);
        assertThat(foundNotice.getCreatedAt().toInstant()).isEqualTo(createdAt.toInstant());
        assertThat(foundRead.getReadAt().toInstant()).isEqualTo(createdAt.plusMinutes(5).toInstant());
    }

    @Test
    @Transactional
    void noticePageQueryExecutesJoinReadFlagSortingFilteringPagingAndCount() {
        Long adminId = insertMember("admin02", "공지관리자", "ADMIN");
        Long studentId = insertMember("student02", "조회학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        OffsetDateTime base = OffsetDateTime.parse("2026-08-23T00:00:00Z");

        Notice pinnedOld = noticeRepository.save(Notice.create(admin, "고정-과거", "내용", true, base));
        Notice pinnedNew = noticeRepository.save(Notice.create(admin, "고정-최신", "내용", true, base.plusHours(1)));
        Notice normalOld = noticeRepository.save(Notice.create(admin, "일반-과거", "내용", false, base.plusHours(2)));
        Notice normalNew = noticeRepository.save(Notice.create(admin, "일반-최신", "내용", false, base.plusHours(3)));
        noticeRepository.flush();
        noticeReadRepository.insertIfAbsent(pinnedNew.getId(), studentId, base.plusHours(4));

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
                .doesNotContain(pinnedNew.getId())
                .contains(pinnedOld.getId(), normalNew.getId(), normalOld.getId());
        assertThat(noticeRepository.countUnreadByMemberId(studentId)).isEqualTo(3);
    }

    @Test
    void nativeInsertIsIdempotentAndSafeUnderConcurrentTransactions() throws Exception {
        Long adminId = insertMember("admin03", "관리자", "ADMIN");
        Long studentId = insertMember("student03", "학생", "STUDENT");
        Member admin = memberRepository.findById(adminId).orElseThrow();
        Notice notice = noticeRepository.save(Notice.create(admin, "동시성", "내용", false, OffsetDateTime.now()));
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
        Notice notice = noticeRepository.save(Notice.create(admin, "제약", "내용", false, OffsetDateTime.now()));

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
        Notice notice = noticeRepository.save(Notice.create(admin, "보존", "내용", false, OffsetDateTime.now()));
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

    private Long insertMember(String studentNumber, String name, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member (student_number, name, password_hash, role, status, created_at, updated_at)
                VALUES (?, ?, 'hash', ?, 'APPROVED', now(), now()) RETURNING id
                """, Long.class, studentNumber, name, role);
    }
}
