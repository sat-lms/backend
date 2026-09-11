package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.AdminMemberDetailResponse;
import com.sat.lms.admin.dto.AdminMemberResponse;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberReview;
import com.sat.lms.member.entity.MemberReviewAction;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.repository.MemberReviewRepository;
import com.sat.lms.member.service.MemberGuard;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminMemberServiceTest {
    private final MemberGuard guard = mock(MemberGuard.class);
    private final MemberRepository repository = mock(MemberRepository.class);
    private final MemberReviewRepository reviewRepository = mock(MemberReviewRepository.class);
    private final AdminMemberService service = new AdminMemberService(guard, repository, reviewRepository);

    @Test
    void adminCanGetMemberDetailWithLatestReviewOnly() {
        Member admin = member(MemberRole.ADMIN);
        Member target = member(MemberRole.STUDENT);
        when(target.getId()).thenReturn(2L);
        when(target.getStudentNumber()).thenReturn("20231234");
        when(target.getName()).thenReturn("최인준");
        when(target.getStatus()).thenReturn(MemberStatus.APPROVED);
        Member reviewer = member(MemberRole.ADMIN);
        when(reviewer.getName()).thenReturn("관리자1");
        MemberReview older = new MemberReview(2L, 9L, MemberReviewAction.REJECTED, "서류 미비",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        MemberReview latest = new MemberReview(2L, 10L, MemberReviewAction.APPROVED, null,
                OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(reviewRepository.findLatestByMemberId(eq(2L), any())).thenReturn(List.of(latest));
        when(repository.findById(10L)).thenReturn(Optional.of(reviewer));

        AdminMemberDetailResponse response = service.getMemberDetail(1L, 2L);

        assertThat(response.getMemberId()).isEqualTo(2L);
        assertThat(response.getAction()).isEqualTo("APPROVED");
        assertThat(response.getRejectionReason()).isNull();
        assertThat(response.getReviewerId()).isEqualTo(10L);
        assertThat(response.getReviewerName()).isEqualTo("관리자1");
        assertThat(response.getReviewedAt()).isEqualTo(latest.getReviewedAt());
        verify(repository, never()).findById(9L);
    }

    @Test
    void memberDetailReviewerIdMatchesTheReviewerWhoActuallyReviewed() {
        // reviewerId가 리뷰어 조회용으로 쓰인 것과 같은 값(=실제로 심사한 관리자 ID)인지
        // 명시적으로 확인한다. reviewerName을 가져올 때 조회한 대상(10L)과 응답의
        // reviewerId가 정확히 같은 회원을 가리켜야 한다.
        Member admin = member(MemberRole.ADMIN);
        Member target = member(MemberRole.STUDENT);
        when(target.getId()).thenReturn(5L);
        when(target.getStatus()).thenReturn(MemberStatus.APPROVED);
        Member reviewer = member(MemberRole.ADMIN);
        when(reviewer.getName()).thenReturn("심사자A");
        MemberReview review = new MemberReview(5L, 42L, MemberReviewAction.APPROVED, null, OffsetDateTime.now());
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(repository.findById(5L)).thenReturn(Optional.of(target));
        when(reviewRepository.findLatestByMemberId(eq(5L), any())).thenReturn(List.of(review));
        when(repository.findById(42L)).thenReturn(Optional.of(reviewer));

        AdminMemberDetailResponse response = service.getMemberDetail(1L, 5L);

        assertThat(response.getReviewerId()).isEqualTo(42L);
        assertThat(response.getReviewerName()).isEqualTo("심사자A");
        verify(repository).findById(42L);
    }

    @Test
    void memberDetailWithNoReviewHistoryLeavesReviewFieldsNull() {
        Member admin = member(MemberRole.ADMIN);
        Member target = member(MemberRole.STUDENT);
        when(target.getId()).thenReturn(3L);
        when(target.getStatus()).thenReturn(MemberStatus.PENDING);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(repository.findById(3L)).thenReturn(Optional.of(target));
        when(reviewRepository.findLatestByMemberId(eq(3L), any())).thenReturn(List.of());

        AdminMemberDetailResponse response = service.getMemberDetail(1L, 3L);

        assertThat(response.getAction()).isNull();
        assertThat(response.getRejectionReason()).isNull();
        assertThat(response.getReviewerId()).isNull();
        assertThat(response.getReviewerName()).isNull();
        assertThat(response.getReviewedAt()).isNull();
    }

    @Test
    void memberDetailForMissingMemberThrowsNotFound() {
        Member admin = member(MemberRole.ADMIN);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMemberDetail(1L, 999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(reviewRepository);
    }

    @Test
    void nonAdminCannotGetMemberDetail() {
        when(guard.requireAdmin(1L)).thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."));

        assertThatThrownBy(() -> service.getMemberDetail(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(repository, reviewRepository);
    }

    @Test
    void adminCanListMembersAndFiltersArePassedThrough() {
        Member admin = member(MemberRole.ADMIN);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        Page<AdminMemberResponse> page = new PageImpl<>(java.util.List.of());
        when(repository.findMemberPage(MemberRole.STUDENT, MemberStatus.APPROVED, "최인준", PageRequest.of(0, 20)))
                .thenReturn(page);

        Page<AdminMemberResponse> result = service.getMembers(1L, MemberRole.STUDENT, MemberStatus.APPROVED,
                "최인준", PageRequest.of(0, 20));

        assertThat(result).isSameAs(page);
        verify(guard).requireAdmin(1L);
    }

    @Test
    void listingMembersWithoutFiltersPassesNullsThrough() {
        Member admin = member(MemberRole.ADMIN);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(repository.findMemberPage(isNull(), isNull(), isNull(), any())).thenReturn(new PageImpl<>(java.util.List.of()));

        service.getMembers(1L, null, null, null, PageRequest.of(0, 20));

        verify(repository).findMemberPage(isNull(), isNull(), isNull(), eq(PageRequest.of(0, 20)));
    }

    @Test
    void nonAdminCannotListMembers() {
        when(guard.requireAdmin(1L)).thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."));

        assertThatThrownBy(() -> service.getMembers(1L, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(repository);
    }

    @Test
    void approvedAdminExpelsApprovedStudentWithConsistentLockOrder() {
        Member admin = member(MemberRole.ADMIN);
        Member student = member(MemberRole.STUDENT);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(guard.requireAdminForUpdate(1L)).thenReturn(admin);
        when(guard.requireMemberForUpdate(2L)).thenReturn(student);

        service.expel(1L, 2L);

        InOrder order = inOrder(guard, repository, student);
        order.verify(guard).requireAdmin(1L);
        order.verify(repository).findFirstByOrderByIdAsc();
        order.verify(guard).requireAdminForUpdate(1L);
        order.verify(guard).requireMemberForUpdate(2L);
        order.verify(student).expel();
        order.verify(repository).flush();
        verify(repository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(repository, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void selfExpulsionIsRejectedBeforeLocksAndMutation() {
        Member admin = member(MemberRole.ADMIN);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        assertThatThrownBy(() -> service.expel(1L, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).findFirstByOrderByIdAsc();
        verify(repository, never()).flush();
    }

    @Test
    void anotherAdminCannotBeExpelled() {
        Member admin = member(MemberRole.ADMIN);
        Member target = member(MemberRole.ADMIN);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(guard.requireAdminForUpdate(1L)).thenReturn(admin);
        when(guard.requireMemberForUpdate(2L)).thenReturn(target);
        assertThatThrownBy(() -> service.expel(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(target, never()).expel();
        verify(repository, never()).flush();
    }

    @Test
    void actorValidationFailureStopsBeforeCoordinationAndTargetLookup() {
        when(guard.requireAdmin(1L)).thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "blocked"));
        assertThatThrownBy(() -> service.expel(1L, 2L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
        verify(guard, never()).requireMemberForUpdate(2L);
    }

    @Test
    void invalidTargetStateStopsBeforeMutationAndFlush() {
        Member admin = member(MemberRole.ADMIN);
        when(guard.requireAdmin(1L)).thenReturn(admin);
        when(guard.requireAdminForUpdate(1L)).thenReturn(admin);
        when(guard.requireMemberForUpdate(2L))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "탈퇴하거나 정지된 계정입니다."));
        assertThatThrownBy(() -> service.expel(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).flush();
    }

    private Member member(MemberRole role) {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(role);
        return member;
    }
}
