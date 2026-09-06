package com.sat.lms.notice.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.notice.dto.NoticeCommentResponse;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.entity.NoticeComment;
import com.sat.lms.notice.repository.NoticeCommentRepository;
import com.sat.lms.notice.repository.NoticeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoticeCommentService {
    private static final String NOT_FOUND_NOTICE_MESSAGE = "존재하지 않는 공지사항입니다.";
    private static final String NOT_FOUND_COMMENT_MESSAGE = "존재하지 않는 댓글입니다.";
    private static final String FORBIDDEN_AUTHOR_MESSAGE = "작성자 본인만 수정할 수 있습니다.";
    private static final String FORBIDDEN_DELETE_MESSAGE = "작성자 본인 또는 관리자만 삭제할 수 있습니다.";

    private final NoticeRepository noticeRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final MemberGuard memberGuard;

    public NoticeCommentService(NoticeRepository noticeRepository,
                                NoticeCommentRepository noticeCommentRepository,
                                MemberGuard memberGuard) {
        this.noticeRepository = noticeRepository;
        this.noticeCommentRepository = noticeCommentRepository;
        this.memberGuard = memberGuard;
    }

    @Transactional
    public NoticeCommentResponse create(Long noticeId, Long memberId, String content) {
        Member requester = memberGuard.requireMember(memberId);
        Notice notice = findNotice(noticeId);

        NoticeComment comment = NoticeComment.create(notice, requester, content);
        return NoticeCommentResponse.from(noticeCommentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public Page<NoticeCommentResponse> getComments(Long noticeId, Long memberId, Pageable pageable) {
        memberGuard.requireMember(memberId);
        findNotice(noticeId);

        return noticeCommentRepository.findWithAuthorByNoticeId(noticeId, pageable)
                .map(NoticeCommentResponse::from);
    }

    @Transactional
    public NoticeCommentResponse update(Long commentId, Long memberId, String content) {
        Member requester = memberGuard.requireMember(memberId);
        NoticeComment comment = findComment(commentId);
        if (!comment.getAuthor().getId().equals(requester.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_AUTHOR_MESSAGE);
        }

        comment.updateContent(content);
        return NoticeCommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long commentId, Long memberId) {
        Member requester = memberGuard.requireMember(memberId);
        NoticeComment comment = findComment(commentId);
        boolean isAuthor = comment.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == MemberRole.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_DELETE_MESSAGE);
        }

        noticeCommentRepository.delete(comment);
    }

    private Notice findNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_NOTICE_MESSAGE));
    }

    private NoticeComment findComment(Long commentId) {
        return noticeCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_COMMENT_MESSAGE));
    }
}
