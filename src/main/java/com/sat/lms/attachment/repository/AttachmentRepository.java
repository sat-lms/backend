package com.sat.lms.attachment.repository;

import com.sat.lms.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}