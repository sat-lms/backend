package com.sat.lms.submission.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.submission.dto.SubmissionCommentResponse;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.entity.SubmissionComment;
import com.sat.lms.submission.repository.SubmissionCommentRepository;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionCommentService {
    private static final String NOT_FOUND_SUBMISSION_MESSAGE = "제출물이 존재하지 않습니다.";
    private static final String NOT_FOUND_COMMENT_MESSAGE = "존재하지 않는 댓글입니다.";
    private static final String FORBIDDEN_OWNER_MESSAGE = "본인 제출물만 접근할 수 있습니다.";
    private static final String FORBIDDEN_AUTHOR_MESSAGE = "작성자 본인만 수정할 수 있습니다.";
    private static final String FORBIDDEN_DELETE_MESSAGE = "작성자 본인 또는 관리자만 삭제할 수 있습니다.";

    private final SubmissionRepository submissionRepository;
    private final SubmissionCommentRepository submissionCommentRepository;
    private final MemberGuard memberGuard;

    public SubmissionCommentService(SubmissionRepository submissionRepository,
                                    SubmissionCommentRepository submissionCommentRepository,
                                    MemberGuard memberGuard) {
        this.submissionRepository = submissionRepository;
        this.submissionCommentRepository = submissionCommentRepository;
        this.memberGuard = memberGuard;
    }

    @Transactional
    public SubmissionCommentResponse create(Long submissionId, Long memberId, String content) {
        Member requester = memberGuard.requireMember(memberId);
        Submission submission = findSubmission(submissionId);
        requireOwnerOrAdmin(requester, submission.getStudent());

        SubmissionComment comment = SubmissionComment.create(submission, requester, content);
        return SubmissionCommentResponse.from(submissionCommentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public Page<SubmissionCommentResponse> getComments(Long submissionId, Long memberId, Pageable pageable) {
        Member requester = memberGuard.requireMember(memberId);
        Submission submission = findSubmission(submissionId);
        requireOwnerOrAdmin(requester, submission.getStudent());

        return submissionCommentRepository.findWithAuthorBySubmissionId(submissionId, pageable)
                .map(SubmissionCommentResponse::from);
    }

    @Transactional
    public SubmissionCommentResponse update(Long commentId, Long memberId, String content) {
        Member requester = memberGuard.requireMember(memberId);
        SubmissionComment comment = findComment(commentId);
        if (!comment.getAuthor().getId().equals(requester.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_AUTHOR_MESSAGE);
        }

        comment.updateContent(content);
        return SubmissionCommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long commentId, Long memberId) {
        Member requester = memberGuard.requireMember(memberId);
        SubmissionComment comment = findComment(commentId);
        boolean isAuthor = comment.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == MemberRole.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_DELETE_MESSAGE);
        }

        submissionCommentRepository.delete(comment);
    }

    private void requireOwnerOrAdmin(Member requester, Member owner) {
        boolean isOwner = requester.getId().equals(owner.getId());
        boolean isAdmin = requester.getRole() == MemberRole.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_OWNER_MESSAGE);
        }
    }

    private Submission findSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_SUBMISSION_MESSAGE));
    }

    private SubmissionComment findComment(Long commentId) {
        return submissionCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_COMMENT_MESSAGE));
    }
}
