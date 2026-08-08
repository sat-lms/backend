package com.sat.lms.member.service;

import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberServiceTest {

    @Test
    void getMeDoesNotExposePasswordHash() {
        MemberRepository repository = mock(MemberRepository.class);
        Member member = Member.createStudent("20231234", "홍길동", "secret-hash", OffsetDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(member));

        MemberMeResponse response = new MemberService(repository).getMe(1L);

        assertThat(response.getStudentNumber()).isEqualTo("20231234");
        assertThat(MemberMeResponse.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("password"));
    }
}
