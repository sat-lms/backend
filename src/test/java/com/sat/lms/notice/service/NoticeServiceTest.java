package com.sat.lms.notice.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.notice.dto.NoticeCreateRequest;
import com.sat.lms.notice.dto.NoticeListResponse;
import com.sat.lms.notice.dto.NoticeUpdateRequest;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.repository.NoticeReadRepository;
import com.sat.lms.notice.repository.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeServiceTest {
    NoticeRepository noticeRepository;
    NoticeReadRepository noticeReadRepository;
    MemberRepository memberRepository;
    NoticeService service;

    @BeforeEach
    void setUp() {
        noticeRepository = mock(NoticeRepository.class);
        noticeReadRepository = mock(NoticeReadRepository.class);
        memberRepository = mock(MemberRepository.class);
        service = new NoticeService(noticeRepository, noticeReadRepository, memberRepository);
    }

    @Test
    void listUsesSinglePagedReadJoinQueryAndPreservesReadFlags() {
        PageRequest pageable = PageRequest.of(0, 20);
        List<NoticeListResponse> content = List.of(
                new NoticeListResponse(1L, "고정", true, OffsetDateTime.now(), "관리자", false),
                new NoticeListResponse(2L, "일반", false, OffsetDateTime.now().minusDays(1), "관리자", true));
        when(noticeRepository.findNoticePage(3L, false, pageable)).thenReturn(new PageImpl<>(content));

        var result = service.getNotices(3L, false, pageable);

        assertThat(result.getContent()).extracting(NoticeListResponse::getIsRead).containsExactly(false, true);
        verify(noticeRepository).findNoticePage(3L, false, pageable);
        verify(noticeReadRepository, never()).findAll();
    }

    @Test
    void unreadOnlyIsPassedToDatabaseQuery() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(noticeRepository.findNoticePage(3L, true, pageable)).thenReturn(new PageImpl<>(List.of()));
        service.getNotices(3L, true, pageable);
        verify(noticeRepository).findNoticePage(3L, true, pageable);
    }

    @Test
    void unreadCountUsesCountQuery() {
        when(noticeRepository.countUnreadByMemberId(3L)).thenReturn(4L);
        assertThat(service.getUnreadCount(3L).getUnreadCount()).isEqualTo(4L);
    }

    @Test
    void detailInsertsReadRecordWithConflictSafeRepositoryOperationEachTime() {
        Notice notice = notice();
        when(noticeRepository.findWithAdminById(1L)).thenReturn(Optional.of(notice));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(mock(Member.class)));

        service.getNotice(1L, 3L);
        service.getNotice(1L, 3L);

        verify(noticeReadRepository, org.mockito.Mockito.times(2))
                .insertIfAbsent(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(3L), any());
    }

    @Test
    void missingNoticeReturnsNotFound() {
        when(noticeRepository.findWithAdminById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getNotice(99L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void partialUpdateChangesOnlyProvidedField() {
        Member admin = admin();
        Notice notice = Notice.create(admin, "기존 제목", "기존 내용", false);
        NoticeUpdateRequest request = new NoticeUpdateRequest();
        request.setTitle("새 제목");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(noticeRepository.findWithAdminById(1L)).thenReturn(Optional.of(notice));

        service.update(1L, request, 7L);

        assertThat(notice.getTitle()).isEqualTo("새 제목");
        assertThat(notice.getContent()).isEqualTo("기존 내용");
        assertThat(notice.isPinned()).isFalse();
    }

    @Test
    void updateResponseIsReadWhenAdminAlreadyReadNotice() {
        Member admin = admin();
        Notice notice = Notice.create(admin, "기존 제목", "기존 내용", false);
        NoticeUpdateRequest request = new NoticeUpdateRequest();
        request.setTitle("새 제목");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(noticeRepository.findWithAdminById(1L)).thenReturn(Optional.of(notice));
        when(noticeReadRepository.existsByNoticeIdAndMemberId(1L, 7L)).thenReturn(true);

        var response = service.update(1L, request, 7L);

        assertThat(response.getIsRead()).isTrue();
        verify(noticeReadRepository).existsByNoticeIdAndMemberId(1L, 7L);
    }

    @Test
    void updateResponseIsUnreadWhenAdminHasNotReadNotice() {
        Member admin = admin();
        Notice notice = Notice.create(admin, "기존 제목", "기존 내용", false);
        NoticeUpdateRequest request = new NoticeUpdateRequest();
        request.setTitle("새 제목");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(noticeRepository.findWithAdminById(1L)).thenReturn(Optional.of(notice));
        when(noticeReadRepository.existsByNoticeIdAndMemberId(1L, 7L)).thenReturn(false);

        var response = service.update(1L, request, 7L);

        assertThat(response.getIsRead()).isFalse();
        verify(noticeReadRepository).existsByNoticeIdAndMemberId(1L, 7L);
    }

    @Test
    void updateAcceptsOneHundredCharacterTitleAndRejectsOneHundredOne() {
        Member admin = admin();
        Notice notice = Notice.create(admin, "기존 제목", "기존 내용", false);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(noticeRepository.findWithAdminById(1L)).thenReturn(Optional.of(notice));
        NoticeUpdateRequest valid = new NoticeUpdateRequest();
        valid.setTitle("a".repeat(100));

        service.update(1L, valid, 7L);
        assertThat(notice.getTitle()).hasSize(100);

        NoticeUpdateRequest invalid = new NoticeUpdateRequest();
        invalid.setTitle("a".repeat(101));
        assertThatThrownBy(() -> service.update(1L, invalid, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void emptyPatchReturnsBadRequest() {
        Member admin = admin();
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> service.update(1L, new NoticeUpdateRequest(), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void missingUpdateAndDeleteReturnNotFound() {
        Member admin = admin();
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(noticeRepository.findWithAdminById(99L)).thenReturn(Optional.empty());
        when(noticeRepository.findById(99L)).thenReturn(Optional.empty());
        NoticeUpdateRequest request = new NoticeUpdateRequest();
        request.setTitle("제목");

        assertThatThrownBy(() -> service.update(99L, request, 7L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(99L, 7L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void adminCanCreateAndDeleteNotice() {
        Member admin = admin();
        NoticeCreateRequest request = mock(NoticeCreateRequest.class);
        when(request.getTitle()).thenReturn("제목");
        when(request.getContent()).thenReturn("내용");
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(noticeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request, 7L);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertThat(captor.getValue().getAdmin()).isSameAs(admin);
    }

    private Notice notice() {
        return Notice.create(admin(), "제목", "내용", false);
    }

    private Member admin() {
        Member admin = mock(Member.class);
        when(admin.getRole()).thenReturn(MemberRole.ADMIN);
        when(admin.getName()).thenReturn("관리자");
        return admin;
    }
}
