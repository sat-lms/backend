package com.sat.lms.member.service;

import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.dto.MemberWithdrawalRequest;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.entity.InvalidMemberStateException;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    @Test
    void getMeDoesNotExposePasswordHash() {
        MemberGuard memberGuard = mock(MemberGuard.class);
        Member member = Member.createStudent("20231234", "홍길동", "secret-hash");
        when(memberGuard.requireMember(1L)).thenReturn(member);

        MemberMeResponse response = new MemberService(memberGuard, mock(MemberRepository.class),
                mock(PasswordEncoder.class)).getMe(1L);

        assertThat(response.getStudentNumber()).isEqualTo("20231234");
        assertThat(MemberMeResponse.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("password"));
    }

    @Test
    void approvedStudentWithCorrectPasswordIsWithdrawnWithoutDelete() {
        MemberGuard guard = mock(MemberGuard.class);
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Member member = mock(Member.class);
        when(guard.requireMemberForUpdate(1L)).thenReturn(member);
        when(member.getPasswordHash()).thenReturn("hash");
        when(member.getRole()).thenReturn(MemberRole.STUDENT);
        when(encoder.matches("Password123", "hash")).thenReturn(true);

        new MemberService(guard, repository, encoder)
                .withdraw(1L, new MemberWithdrawalRequest("Password123"));

        verify(repository).findFirstByOrderByIdAsc();
        verify(guard).requireMemberForUpdate(1L);
        verify(encoder).matches("Password123", "hash");
        verify(member).withdraw();
        verify(repository).flush();
        verify(repository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(repository, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void wrongPasswordLeavesMemberUnchanged() {
        MemberGuard guard = mock(MemberGuard.class);
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Member member = mock(Member.class);
        when(guard.requireMemberForUpdate(1L)).thenReturn(member);
        when(member.getPasswordHash()).thenReturn("hash");

        assertThatThrownBy(() -> new MemberService(guard, repository, encoder)
                .withdraw(1L, new MemberWithdrawalRequest("wrong")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getMessage()).isEqualTo("현재 비밀번호가 올바르지 않습니다.");
                });

        verify(member, never()).withdraw();
        verify(repository, never()).flush();
    }

    @Test
    void lastApprovedAdminCannotWithdraw() {
        MemberGuard guard = mock(MemberGuard.class);
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Member member = mock(Member.class);
        when(guard.requireMemberForUpdate(1L)).thenReturn(member);
        when(member.getPasswordHash()).thenReturn("hash");
        when(member.getRole()).thenReturn(MemberRole.ADMIN);
        when(encoder.matches("Password123", "hash")).thenReturn(true);
        when(repository.countByRoleAndStatus(MemberRole.ADMIN, MemberStatus.APPROVED)).thenReturn(1L);

        assertThatThrownBy(() -> new MemberService(guard, repository, encoder)
                .withdraw(1L, new MemberWithdrawalRequest("Password123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(member, never()).withdraw();
    }

    @Test
    void approvedAdminCanWithdrawWhenAnotherApprovedAdminRemains() {
        MemberGuard guard = mock(MemberGuard.class);
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Member member = mock(Member.class);
        when(guard.requireMemberForUpdate(1L)).thenReturn(member);
        when(member.getPasswordHash()).thenReturn("hash");
        when(member.getRole()).thenReturn(MemberRole.ADMIN);
        when(encoder.matches("Password123", "hash")).thenReturn(true);
        when(repository.countByRoleAndStatus(MemberRole.ADMIN, MemberStatus.APPROVED)).thenReturn(2L);

        new MemberService(guard, repository, encoder)
                .withdraw(1L, new MemberWithdrawalRequest("Password123"));
        verify(member).withdraw();
    }

    @Test
    void unexpectedStaleDomainStateIsMappedOnlyForWithdrawalToForbidden() {
        MemberGuard guard = mock(MemberGuard.class);
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Member member = mock(Member.class);
        when(guard.requireMemberForUpdate(1L)).thenReturn(member);
        when(member.getPasswordHash()).thenReturn("hash");
        when(member.getRole()).thenReturn(MemberRole.STUDENT);
        when(encoder.matches("Password123", "hash")).thenReturn(true);
        doThrow(new InvalidMemberStateException("stale state")).when(member).withdraw();

        assertThatThrownBy(() -> new MemberService(guard, repository, encoder)
                .withdraw(1L, new MemberWithdrawalRequest("Password123")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getMessage()).isEqualTo("탈퇴하거나 정지된 계정입니다.");
                });
        verify(repository, never()).flush();
    }
}
