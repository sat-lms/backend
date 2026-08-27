package com.sat.lms.attachment.repository;

import com.sat.lms.attachment.entity.Attachment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    @Query("select count(aa.id) > 0 from AssignmentAttachment aa where aa.attachment.id = :attachmentId")
    boolean existsAssignmentLink(@Param("attachmentId") Long attachmentId);

    @Query("select count(sa.id) > 0 from SubmissionAttachment sa where sa.attachment.id = :attachmentId")
    boolean existsSubmissionLink(@Param("attachmentId") Long attachmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attachment a where a.id = :attachmentId")
    Optional<Attachment> findByIdForUpdate(@Param("attachmentId") Long attachmentId);
}
