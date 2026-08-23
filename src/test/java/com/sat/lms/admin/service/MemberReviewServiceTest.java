package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberReview;
import com.sat.lms.member.entity.MemberReviewAction;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.repository.MemberReviewRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberReviewServiceTest {

    @Test
    void savesAuthenticatedAdminMemberIdAsReviewerId() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        MemberReviewRepository memberReviewRepository = mock(MemberReviewRepository.class);
        MemberReviewService service = new MemberReviewService(memberRepository, memberReviewRepository);
        Member pendingMember = Member.createStudent("2026000001", "학생", "hash", OffsetDateTime.now());
        MemberReviewRequest request = mock(MemberReviewRequest.class);
        when(request.getAction()).thenReturn(MemberReviewAction.APPROVED);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(pendingMember));

        service.review(10L, request, 7L);

        ArgumentCaptor<MemberReview> reviewCaptor = ArgumentCaptor.forClass(MemberReview.class);
        verify(memberReviewRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getReviewerId()).isEqualTo(7L);
    }
}
