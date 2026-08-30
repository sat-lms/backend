package com.sat.lms.member.service;

import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.entity.Member;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    @Test
    void getMeDoesNotExposePasswordHash() {
        MemberGuard memberGuard = mock(MemberGuard.class);
        Member member = Member.createStudent("20231234", "홍길동", "secret-hash");
        when(memberGuard.requireMember(1L)).thenReturn(member);

        MemberMeResponse response = new MemberService(memberGuard).getMe(1L);

        assertThat(response.getStudentNumber()).isEqualTo("20231234");
        assertThat(MemberMeResponse.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("password"));
    }
}
