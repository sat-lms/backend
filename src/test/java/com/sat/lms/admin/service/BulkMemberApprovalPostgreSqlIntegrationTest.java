package com.sat.lms.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.admin.dto.*;
import com.sat.lms.auth.dto.ReactivationRequest;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.member.dto.MemberWithdrawalRequest;
import com.sat.lms.member.service.MemberService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BulkMemberApprovalPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bulk_approval_test").withUsername("test").withPassword("test");
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",POSTGRES::getDriverClassName);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberReviewService service;
    @Autowired MemberService memberService;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean FileStorage storage;
    Long admin;
    int sequence;

    @BeforeEach
    void setup() {
        // This class owns a fresh isolated container; no existing local database is used.
        jdbc.update("DELETE FROM member_review");
        jdbc.update("DELETE FROM member");
        sequence = 0;
        admin = member("ADMIN","APPROVED");
    }
    @AfterEach
    void noStorageCalls() { verifyNoInteractions(storage); }

    @Test
    void mixedRequestPreservesSkippedRowsAndReviewHistoryAndResponseOrder() throws Exception {
        Long pending = member("STUDENT","PENDING");
        Long otherPending = member("STUDENT","PENDING");
        Long approved = member("STUDENT","APPROVED");
        Long rejected = member("STUDENT","REJECTED");
        Long withdrawn = member("STUDENT","WITHDRAWN");
        Long unselected = member("STUDENT","PENDING");
        history(approved);
        var skippedBefore = List.of(row(approved),row(rejected),row(withdrawn));
        var pendingBefore = row(pending);
        List<Long> ids = List.of(withdrawn,pending,999999L,approved,pending,rejected,otherPending);
        var result = service.bulkApprove(new BulkMemberApprovalRequest(ids),admin);
        assertThat(result.requestedCount()).isEqualTo(6);
        assertThat(result.approvedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(4);
        assertThat(result.results()).extracting(BulkMemberApprovalResponse.Result::memberId)
                .containsExactly(withdrawn,pending,999999L,approved,rejected,otherPending);
        assertThat(result.results()).extracting(BulkMemberApprovalResponse.Result::reason)
                .containsExactly(BulkMemberApprovalResponse.Reason.NOT_PENDING,null,BulkMemberApprovalResponse.Reason.NOT_FOUND,
                        BulkMemberApprovalResponse.Reason.NOT_PENDING,BulkMemberApprovalResponse.Reason.NOT_PENDING,null);
        assertThat(List.of(row(approved),row(rejected),row(withdrawn))).isEqualTo(skippedBefore);
        assertThat(memberStatus(unselected)).isEqualTo("PENDING");
        assertThat(reviewCount(unselected)).isZero();
        assertThat(reviewCount(pending)).isOne();
        assertThat(reviewCount(otherPending)).isOne();
        assertThat(reviewCount(approved)).isOne();
        assertThat(reviewCount(rejected)).isZero();
        assertThat(reviewCount(withdrawn)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member",Integer.class)).isEqualTo(7);
        assertPreservedFields(pendingBefore,row(pending));
        assertThat(row(pending).get("updated_at")).isNotEqualTo(pendingBefore.get("updated_at"));
        var beforeAllSkipped = jdbc.queryForList("SELECT * FROM member ORDER BY id");
        var reviewsBeforeAllSkipped = jdbc.queryForList("SELECT * FROM member_review ORDER BY id");
        mvc.perform(post("/api/v1/admin/member-applications/bulk-approve")
                .header("Authorization","Bearer " + tokens.createAccessToken(admin,"ADMIN"))
                .contentType("application/json").content(mapper.writeValueAsString(new BulkMemberApprovalRequest(ids))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.approvedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(6)).andExpect(jsonPath("$.data.results[0].memberId").value(withdrawn));
        assertThat(reviewCount(pending)).isOne();
        assertThat(jdbc.queryForList("SELECT * FROM member ORDER BY id")).isEqualTo(beforeAllSkipped);
        assertThat(jdbc.queryForList("SELECT * FROM member_review ORDER BY id")).isEqualTo(reviewsBeforeAllSkipped);
    }

    @Test
    void approvesOneHundredDistinctPendingMembers() {
        List<Long> ids = new ArrayList<>();
        for (int i=0;i<100;i++) ids.add(member("STUDENT","PENDING"));
        var result = service.bulkApprove(new BulkMemberApprovalRequest(ids),admin);
        assertThat(result.requestedCount()).isEqualTo(100);
        assertThat(result.approvedCount()).isEqualTo(100);
        assertThat(result.skippedCount()).isZero();
        for (Long id:ids) { assertThat(memberStatus(id)).isEqualTo("APPROVED"); assertThat(reviewCount(id)).isOne(); }
    }

    @Test
    void reactivationUsesExistingPolicyAndPreservesHistoricalReviews() {
        Long student = member("STUDENT","APPROVED");
        history(student);
        var oldHistory = jdbc.queryForList("SELECT * FROM member_review WHERE member_id=?",student);
        String number = (String)row(student).get("student_number");
        memberService.withdraw(student,new MemberWithdrawalRequest("Password123"));
        authService.requestReactivation(new ReactivationRequest(number,"Password123","Password123"));
        var before = row(student);
        assertThat(memberStatus(student)).isEqualTo("PENDING");
        assertThat(service.bulkApprove(new BulkMemberApprovalRequest(List.of(student)),admin).approvedCount()).isOne();
        assertPreservedFields(before,row(student));
        assertThat(row(student).get("token_version")).isEqualTo(1L);
        assertThat(row(student).get("deactivation_reason")).isNull();
        assertThat(reviewCount(student)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT * FROM member_review WHERE member_id=? ORDER BY id",student).get(0)).isEqualTo(oldHistory.get(0));
    }

    @Test
    void databaseFailureAfterFirstApprovalRollsBackAllRowsAndReviews() throws Exception {
        Long first = member("STUDENT","PENDING");
        Long second = member("STUDENT","PENDING");
        history(first);
        var before = List.of(row(first),row(second));
        var oldReviews = jdbc.queryForList("SELECT * FROM member_review ORDER BY id");
        jdbc.execute("ALTER TABLE member_review ADD CONSTRAINT test_bulk_failure CHECK (member_id <> " + second + ")");
        try {
            mvc.perform(post("/api/v1/admin/member-applications/bulk-approve")
                    .header("Authorization","Bearer " + tokens.createAccessToken(admin,"ADMIN"))
                    .contentType("application/json").content(mapper.writeValueAsString(new BulkMemberApprovalRequest(List.of(first,second)))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
            assertThat(List.of(row(first),row(second))).isEqualTo(before);
            assertThat(jdbc.queryForList("SELECT * FROM member_review ORDER BY id")).isEqualTo(oldReviews);
        } finally { jdbc.execute("ALTER TABLE member_review DROP CONSTRAINT test_bulk_failure"); }
    }

    @Test
    void currentDatabaseRoleAndStatusOverrideJwtRole() throws Exception {
        Long target = member("STUDENT","PENDING");
        for (String state:List.of("PENDING","REJECTED","WITHDRAWN")) {
            Long inactive = member("ADMIN",state);
            assertForbiddenToken(inactive,target);
        }
        Long student = member("STUDENT","APPROVED");
        assertForbiddenToken(student,target); // deliberately ADMIN claim for a DB student
        assertThat(memberStatus(target)).isEqualTo("PENDING");
        assertThat(reviewCount(target)).isZero();
    }

    @Test
    void individualApprovalAndRejectionKeepContractAndAppendOneReview() throws Exception {
        for (String action:List.of("APPROVED","REJECTED")) {
            Long target = member("STUDENT","PENDING");
            String body = "{\"action\":\""+action+"\",\"rejectionReason\":\"조건 미충족\"}";
            mvc.perform(patch("/api/v1/admin/member-applications/{id}",target)
                    .header("Authorization","Bearer " + tokens.createAccessToken(admin,"ADMIN"))
                    .contentType("application/json").content(body)).andExpect(status().isOk());
            var before = row(target);
            mvc.perform(patch("/api/v1/admin/member-applications/{id}",target)
                    .header("Authorization","Bearer " + tokens.createAccessToken(admin,"ADMIN"))
                    .contentType("application/json").content(body)).andExpect(status().isConflict());
            assertThat(row(target)).isEqualTo(before);
            assertThat(memberStatus(target)).isEqualTo(action);
            assertThat(reviewCount(target)).isOne();
        }
    }

    @Test
    void concurrentBulkApprovalRecordsOnlyOneReview() throws Exception {
        Long secondAdmin = member("ADMIN","APPROVED");
        Long target = member("STUDENT","PENDING");
        var outcomes = race(() -> bulk(admin,List.of(target)),() -> bulk(secondAdmin,List.of(target)));
        assertThat(outcomes).allMatch(BulkMemberApprovalResponse.class::isInstance);
        assertThat(outcomes.stream().mapToInt(o -> ((BulkMemberApprovalResponse)o).approvedCount()).sum()).isOne();
        assertThat(outcomes.stream().mapToInt(o -> ((BulkMemberApprovalResponse)o).skippedCount()).sum()).isOne();
        assertThat(memberStatus(target)).isEqualTo("APPROVED");
        assertThat(reviewCount(target)).isOne();
    }
    @Test
    void individualApprovalAndBulkApprovalCompeteSafely() throws Exception { raceWithIndividual("APPROVED"); }
    @Test
    void individualRejectionAndBulkApprovalCompeteSafely() throws Exception { raceWithIndividual("REJECTED"); }
    private void raceWithIndividual(String action) throws Exception {
        Long secondAdmin = member("ADMIN","APPROVED");
        Long target = member("STUDENT","PENDING");
        MemberReviewRequest request = mapper.readValue("{\"action\":\""+action+"\",\"rejectionReason\":\"검증 실패\"}",MemberReviewRequest.class);
        var outcomes = race(() -> bulk(admin,List.of(target)),() -> {
            try { return service.review(target,request,secondAdmin); }
            catch (BusinessException e) { assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT); return e; }
        });
        var bulk = (BulkMemberApprovalResponse)outcomes.get(0);
        assertThat(bulk.requestedCount()).isOne();
        if (outcomes.get(1) instanceof BusinessException) {
            assertThat(bulk.approvedCount()).isOne();
            assertThat(memberStatus(target)).isEqualTo("APPROVED");
        } else {
            assertThat(bulk.skippedCount()).isOne();
            assertThat(bulk.results().get(0).reason()).isEqualTo(BulkMemberApprovalResponse.Reason.NOT_PENDING);
            assertThat(memberStatus(target)).isEqualTo(action);
        }
        assertThat(reviewCount(target)).isOne();
        assertThat(jdbc.queryForObject("SELECT action FROM member_review WHERE member_id=?",String.class,target)).isEqualTo(memberStatus(target));
    }
    @Test
    void overlappingReverseOrderRequestsDoNotDeadlockOrDuplicateReviews() throws Exception {
        Long secondAdmin = member("ADMIN","APPROVED");
        Long a=member("STUDENT","PENDING"), b=member("STUDENT","PENDING"), c=member("STUDENT","PENDING");
        var outcomes = race(() -> bulk(admin,List.of(a,b)),() -> bulk(secondAdmin,List.of(c,b,a)));
        assertThat(outcomes).allMatch(BulkMemberApprovalResponse.class::isInstance);
        assertThat(((BulkMemberApprovalResponse)outcomes.get(0)).results()).extracting(BulkMemberApprovalResponse.Result::memberId).containsExactly(a,b);
        assertThat(((BulkMemberApprovalResponse)outcomes.get(1)).results()).extracting(BulkMemberApprovalResponse.Result::memberId).containsExactly(c,b,a);
        assertThat(outcomes.stream().mapToInt(o -> ((BulkMemberApprovalResponse)o).approvedCount()).sum()).isEqualTo(3);
        for (Long id:List.of(a,b,c)) { assertThat(memberStatus(id)).isEqualTo("APPROVED"); assertThat(reviewCount(id)).isOne(); }
    }

    @Test
    void actorWithdrawalAndApprovalHaveOneSerializableOutcome() throws Exception {
        member("ADMIN","APPROVED");
        Long target=member("STUDENT","PENDING");
        var outcomes=race(() -> {
            try { return bulk(admin,List.of(target)); }
            catch (BusinessException e) { assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN); return e; }
        },() -> { memberService.withdraw(admin,new MemberWithdrawalRequest("Password123")); return "withdrawn"; });
        assertThat(memberStatus(admin)).isEqualTo("WITHDRAWN");
        if (outcomes.get(0) instanceof BulkMemberApprovalResponse response) {
            assertThat(response.approvedCount()).isOne(); assertThat(memberStatus(target)).isEqualTo("APPROVED"); assertThat(reviewCount(target)).isOne();
        } else { assertThat(memberStatus(target)).isEqualTo("PENDING"); assertThat(reviewCount(target)).isZero(); }
    }

    @Test
    void permissionChangeHoldingCoordinationLockIsRevalidatedAfterWait() throws Exception {
        Long target=member("STUDENT","PENDING");
        var locked=new CountDownLatch(1);
        var started=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var change=executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM member ORDER BY id LIMIT 1 FOR UPDATE",Long.class);
                jdbc.update("UPDATE member SET role='STUDENT' WHERE id=?",admin);
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var approval=executor.submit(() -> { started.countDown(); return bulk(admin,List.of(target)); });
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();
            awaitDatabaseLockWaiters(1);
            release.countDown();
            change.get(15,TimeUnit.SECONDS);
            assertThatThrownBy(() -> approval.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException)e.getCause()).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThat(memberStatus(target)).isEqualTo("PENDING"); assertThat(reviewCount(target)).isZero();
        } finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }

    private List<Object> race(Supplier<Object> first,Supplier<Object> second) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            // Hold the real coordination row until both competing transactions are observed
            // waiting in PostgreSQL. This proves overlap rather than relying on scheduling.
            List<Future<Object>> futures = new TransactionTemplate(transactionManager).execute(tx -> {
                jdbc.queryForObject("SELECT id FROM member ORDER BY id LIMIT 1 FOR UPDATE",Long.class);
                var one=executor.submit(() -> { ready.countDown(); await(start); return first.get(); });
                var two=executor.submit(() -> { ready.countDown(); await(start); return second.get(); });
                await(ready); start.countDown();
                awaitDatabaseLockWaiters(2);
                return List.of(one,two);
            });
            var one=futures.get(0); var two=futures.get(1);
            return List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    private void awaitDatabaseLockWaiters(int expected) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime()<deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            Integer waiting=jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname=current_database() AND pid<>pg_backend_pid() AND wait_event_type='Lock'
                    """,Integer.class);
            if (waiting>=expected) return;
        }
        throw new AssertionError("PostgreSQL lock waiters did not reach " + expected);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10,TimeUnit.SECONDS)) throw new AssertionError("Latch timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private BulkMemberApprovalResponse bulk(Long actor,List<Long> ids) { return service.bulkApprove(new BulkMemberApprovalRequest(ids),actor); }
    private void assertForbiddenToken(Long actor,Long target) throws Exception {
        mvc.perform(post("/api/v1/admin/member-applications/bulk-approve").header("Authorization","Bearer " + tokens.createAccessToken(actor,"ADMIN"))
                .contentType("application/json").content(mapper.writeValueAsString(new BulkMemberApprovalRequest(List.of(target)))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
    }
    private Long member(String role,String state) {
        return jdbc.queryForObject("""
                INSERT INTO member(student_number,name,password_hash,role,status,created_at,updated_at)
                VALUES (?, '테스트', ?, ?, ?, now()-interval '1 day',now()-interval '1 day') RETURNING id
                """,Long.class,String.format("%08d",++sequence),encoder.encode("Password123"),role,state);
    }
    private void history(Long target) { jdbc.update("INSERT INTO member_review(member_id,reviewer_id,action,reviewed_at) VALUES (?,?,'APPROVED',now()-interval '1 day')",target,admin); }
    private Map<String,Object> row(Long id) { return jdbc.queryForMap("SELECT * FROM member WHERE id=?",id); }
    private String memberStatus(Long id) { return jdbc.queryForObject("SELECT status FROM member WHERE id=?",String.class,id); }
    private int reviewCount(Long id) { return jdbc.queryForObject("SELECT count(*) FROM member_review WHERE member_id=?",Integer.class,id); }
    private void assertPreservedFields(Map<String,Object> before,Map<String,Object> after) {
        for (String field:List.of("id","student_number","name","password_hash","role","created_at","token_version","deactivation_reason"))
            assertThat(after.get(field)).as(field).isEqualTo(before.get(field));
    }
}
