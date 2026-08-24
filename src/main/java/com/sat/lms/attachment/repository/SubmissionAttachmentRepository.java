package com.sat.lms.attachment.repository;

import com.sat.lms.attachment.entity.SubmissionAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, Long> {
    @EntityGraph(attributePaths = "attachment")
    @Query("select sa from SubmissionAttachment sa where sa.submission.id = :submissionId")
    List<SubmissionAttachment> findWithAttachmentBySubmissionId(@Param("submissionId") Long submissionId);
}