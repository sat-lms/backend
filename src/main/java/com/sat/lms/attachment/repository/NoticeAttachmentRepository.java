package com.sat.lms.attachment.repository;

import com.sat.lms.attachment.entity.NoticeAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NoticeAttachmentRepository extends JpaRepository<NoticeAttachment, Long> {
    @EntityGraph(attributePaths = {"notice", "attachment"})
    @Query("select na from NoticeAttachment na where na.attachment.id = :attachmentId")
    Optional<NoticeAttachment> findWithNoticeAndAttachmentByAttachmentId(
            @Param("attachmentId") Long attachmentId);

    long countByAttachmentId(Long attachmentId);
}
