package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.BulkMemberApprovalRequest;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse.Outcome;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse.Reason;
import com.sat.lms.member.entity.*;
import com.sat.lms.member.repository.*;
import com.sat.lms.member.service.MemberGuard;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BulkMemberApprovalServiceTest {
    @Test
    void deduplicatesPreservesOrderAndLocksBeforeReviewing() {
        var members = mock(MemberRepository.class);
        var reviews = mock(MemberReviewRepository.class);
        var guard = mock(MemberGuard.class);
        var service = new MemberReviewService(members, reviews, guard);
        var pending = Member.createStudent("20260001", "학생", "hash");
        var approved = Member.createStudent("20260002", "학생", "hash");
        approved.applyReviewResult(MemberStatus.APPROVED);
        when(members.findByIdForUpdate(12L)).thenReturn(Optional.of(pending));
        when(members.findByIdForUpdate(15L)).thenReturn(Optional.of(approved));
        when(members.findByIdForUpdate(18L)).thenReturn(Optional.empty());
        var result = service.bulkApprove(new BulkMemberApprovalRequest(List.of(18L,15L,12L,18L)), 7L);
        assertThat(result.requestedCount()).isEqualTo(3);
        assertThat(result.approvedCount()).isOne();
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.results()).extracting(r -> r.memberId()).containsExactly(18L,15L,12L);
        assertThat(result.results()).extracting(r -> r.result()).containsExactly(Outcome.SKIPPED,Outcome.SKIPPED,Outcome.APPROVED);
        assertThat(result.results()).extracting(r -> r.reason()).containsExactly(Reason.NOT_FOUND,Reason.NOT_PENDING,null);
        var order = inOrder(members,guard,reviews);
        order.verify(members).findFirstByOrderByIdAsc();
        order.verify(guard).requireAdminForUpdate(7L);
        order.verify(members).findByIdForUpdate(12L);
        order.verify(reviews).save(any(MemberReview.class));
        order.verify(members).findByIdForUpdate(15L);
        order.verify(members).findByIdForUpdate(18L);
        verify(reviews, times(1)).save(any());
        assertThat(pending.getStatus()).isEqualTo(MemberStatus.APPROVED);
        assertThat(pending.getPasswordHash()).isEqualTo("hash");
        assertThat(pending.getTokenVersion()).isZero();
    }

    @Test
    void unexpectedSaveFailurePropagatesWithoutProcessingFurtherIds() {
        var members = mock(MemberRepository.class);
        var reviews = mock(MemberReviewRepository.class);
        var service = new MemberReviewService(members,reviews,mock(MemberGuard.class));
        when(members.findByIdForUpdate(12L)).thenReturn(Optional.of(Member.createStudent("20260001","학생","hash")));
        var failure = new IllegalStateException("storage failed");
        when(reviews.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.bulkApprove(new BulkMemberApprovalRequest(List.of(12L,15L)),7L)).isSameAs(failure);
        verify(members,never()).findByIdForUpdate(15L);
    }
}
