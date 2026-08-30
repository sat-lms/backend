package com.sat.lms.notice.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.notice.dto.NoticeCreateRequest;
import com.sat.lms.notice.dto.NoticeDetailResponse;
import com.sat.lms.notice.dto.NoticeListResponse;
import com.sat.lms.notice.dto.NoticeUpdateRequest;
import com.sat.lms.notice.dto.UnreadCountResponse;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.repository.NoticeReadRepository;
import com.sat.lms.notice.repository.NoticeRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.service.MemberGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.sat.lms.notice.entity.Notice.TITLE_MAX_LENGTH;

@Service
@Transactional(readOnly = true)
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final NoticeReadRepository noticeReadRepository;
    private final MemberGuard memberGuard;
    private final NoticeAttachmentCleanup attachmentCleanup;
    private final NoticeAttachmentRepository noticeAttachmentRepository;

    public NoticeService(NoticeRepository noticeRepository, NoticeReadRepository noticeReadRepository,
                         MemberGuard memberGuard, NoticeAttachmentCleanup attachmentCleanup,
                         NoticeAttachmentRepository noticeAttachmentRepository) {
        this.noticeRepository = noticeRepository;
        this.noticeReadRepository = noticeReadRepository;
        this.memberGuard = memberGuard;
        this.attachmentCleanup = attachmentCleanup;
        this.noticeAttachmentRepository = noticeAttachmentRepository;
    }

    public Page<NoticeListResponse> getNotices(Long memberId, boolean unreadOnly, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return noticeRepository.findNoticePage(memberId, unreadOnly, unsorted);
    }

    public UnreadCountResponse getUnreadCount(Long memberId) {
        return new UnreadCountResponse(noticeRepository.countUnreadByMemberId(memberId));
    }

    // 조회처럼 보이지만 insertIfAbsent()가 native INSERT를 실행하므로 쓰기 트랜잭션이 필요합니다.
    // 이 클래스 기본값인 readOnly=true로 되돌리면 INSERT가 런타임에 실패합니다.
    @Transactional
    public NoticeDetailResponse getNotice(Long noticeId, Long memberId) {
        Notice notice = getNoticeWithAdmin(noticeId);
        memberGuard.requireMember(memberId);
        noticeReadRepository.insertIfAbsent(noticeId, memberId, OffsetDateTime.now(ZoneOffset.UTC));
        return NoticeDetailResponse.from(notice, true,
                noticeAttachmentRepository.findWithAttachmentByNoticeId(noticeId));
    }

    @Transactional
    public NoticeDetailResponse create(NoticeCreateRequest request, Long memberId) {
        Member admin = memberGuard.requireAdmin(memberId);
        Notice notice = Notice.create(admin, request.getTitle().trim(), request.getContent().trim(),
                Boolean.TRUE.equals(request.getIsPinned()));
        return NoticeDetailResponse.from(noticeRepository.save(notice), false);
    }

    @Transactional
    public NoticeDetailResponse update(Long noticeId, NoticeUpdateRequest request, Long memberId) {
        memberGuard.requireAdmin(memberId);
        validateUpdate(request);
        Notice notice = getNoticeWithAdmin(noticeId);
        String title = request.isTitlePresent() ? request.getTitle().trim() : null;
        String content = request.isContentPresent() ? request.getContent().trim() : null;
        Boolean pinned = request.isPinnedPresent() ? request.getIsPinned() : null;
        notice.update(title, content, pinned);
        noticeRepository.flush();
        boolean isRead = noticeReadRepository.existsByNoticeIdAndMemberId(noticeId, memberId);
        return NoticeDetailResponse.from(notice, isRead,
                noticeAttachmentRepository.findWithAttachmentByNoticeId(noticeId));
    }

    @Transactional
    public void delete(Long noticeId, Long memberId) {
        memberGuard.requireAdmin(memberId);
        Notice notice = noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(this::noticeNotFound);
        attachmentCleanup.deleteAllForNotice(noticeId);
        noticeRepository.delete(notice);
        noticeRepository.flush();
    }

    private void validateUpdate(NoticeUpdateRequest request) {
        if (request.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "수정할 필드를 입력해주세요.");
        if (request.isTitlePresent() && (request.getTitle() == null || request.getTitle().isBlank()
                || request.getTitle().length() > TITLE_MAX_LENGTH)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "제목은 공백이 아닌 1~100자여야 합니다.");
        }
        if (request.isContentPresent() && (request.getContent() == null || request.getContent().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "내용은 공백일 수 없습니다.");
        }
        if (request.isPinnedPresent() && request.getIsPinned() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "고정 여부는 null일 수 없습니다.");
        }
    }

    private Notice getNoticeWithAdmin(Long noticeId) {
        return noticeRepository.findWithAdminById(noticeId).orElseThrow(this::noticeNotFound);
    }

    private BusinessException noticeNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 공지사항입니다.");
    }
}
