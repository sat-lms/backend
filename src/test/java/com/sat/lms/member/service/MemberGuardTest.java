package com.sat.lms.member.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberGuardTest {
    MemberRepository memberRepository = mock(MemberRepository.class);
    MemberGuard guard = new MemberGuard(memberRepository);

    @Test
    void requireMemberReturnsApprovedMember() {
        Member member = member(MemberRole.STUDENT, MemberStatus.APPROVED);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(guard.requireMember(1L)).isSameAs(member);
    }

    @Test
    void requireMemberRejectsUnknownMember() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireMember(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void requireMemberRejectsNonApprovedStatuses() {
        for (MemberStatus status : new MemberStatus[]{MemberStatus.PENDING, MemberStatus.REJECTED, MemberStatus.WITHDRAWN}) {
            Member member = member(MemberRole.STUDENT, status);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

            assertThatThrownBy(() -> guard.requireMember(1L))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(e.getMessage()).isEqualTo("탈퇴하거나 정지된 계정입니다.");
                    });
        }
    }

    @Test
    void requireAdminAllowsApprovedAdmin() {
        Member admin = member(MemberRole.ADMIN, MemberStatus.APPROVED);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));

        assertThat(guard.requireAdmin(7L)).isSameAs(admin);
    }

    @Test
    void requireAdminRejectsApprovedStudent() {
        Member student = member(MemberRole.STUDENT, MemberStatus.APPROVED);
        when(memberRepository.findById(3L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> guard.requireAdmin(3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void requireAdminRejectsWithdrawnAdminBeforeCheckingRole() {
        Member withdrawnAdmin = member(MemberRole.ADMIN, MemberStatus.WITHDRAWN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(withdrawnAdmin));

        assertThatThrownBy(() -> guard.requireAdmin(7L))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo("탈퇴하거나 정지된 계정입니다.");
                });
    }

    @Test
    void requireStudentAllowsApprovedStudent() {
        Member student = member(MemberRole.STUDENT, MemberStatus.APPROVED);
        when(memberRepository.findById(3L)).thenReturn(Optional.of(student));

        assertThat(guard.requireStudent(3L)).isSameAs(student);
    }

    @Test
    void requireStudentRejectsApprovedAdmin() {
        Member admin = member(MemberRole.ADMIN, MemberStatus.APPROVED);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> guard.requireStudent(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private Member member(MemberRole role, MemberStatus status) {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(role);
        when(member.getStatus()).thenReturn(status);
        return member;
    }
}
