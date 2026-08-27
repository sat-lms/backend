package com.sat.lms.attachment.repository;

import com.sat.lms.attachment.entity.SubmissionAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, Long> {
    @EntityGraph(attributePaths = "attachment")
    @Query("select sa from SubmissionAttachment sa where sa.submission.id = :submissionId")
    List<SubmissionAttachment> findWithAttachmentBySubmissionId(@Param("submissionId") Long submissionId);

    @EntityGraph(attributePaths = "attachment")
    @Query("select sa from SubmissionAttachment sa where sa.submission.id in :submissionIds")
    List<SubmissionAttachment> findWithAttachmentBySubmissionIdIn(
            @Param("submissionIds") List<Long> submissionIds);

    @EntityGraph(attributePaths = {"attachment", "submission", "submission.student"})
    @Query("select sa from SubmissionAttachment sa where sa.attachment.id = :attachmentId")
    Optional<SubmissionAttachment> findWithSubmissionAndAttachmentByAttachmentId(
            @Param("attachmentId") Long attachmentId);

    long countBySubmissionId(Long submissionId);
}