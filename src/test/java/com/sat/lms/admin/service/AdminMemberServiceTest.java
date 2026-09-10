package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.AdminMemberResponse;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.service.MemberGuard;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

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
    private final AdminMemberService service = new AdminMemberService(guard, repository);

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
