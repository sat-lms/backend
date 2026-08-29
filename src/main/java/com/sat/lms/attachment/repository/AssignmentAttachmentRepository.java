package com.sat.lms.attachment.repository;

import com.sat.lms.attachment.entity.AssignmentAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachment, Long> {
    @EntityGraph(attributePaths = {"assignment", "attachment"})
    @Query("select aa from AssignmentAttachment aa where aa.attachment.id = :attachmentId")
    Optional<AssignmentAttachment> findWithAssignmentAndAttachmentByAttachmentId(
            @Param("attachmentId") Long attachmentId);

    @EntityGraph(attributePaths = "attachment")
    @Query("select aa from AssignmentAttachment aa where aa.assignment.id = :assignmentId order by aa.attachment.id")
    List<AssignmentAttachment> findWithAttachmentByAssignmentId(@Param("assignmentId") Long assignmentId);

    long countByAssignmentId(Long assignmentId);
    long countByAttachmentId(Long attachmentId);
}
