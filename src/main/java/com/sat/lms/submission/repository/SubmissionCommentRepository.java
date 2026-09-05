package com.sat.lms.submission.repository;

import com.sat.lms.submission.entity.SubmissionComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionCommentRepository extends JpaRepository<SubmissionComment, Long> {

    @Query(value = "select c from SubmissionComment c join fetch c.author where c.submission.id = :submissionId "
                    + "order by c.createdAt asc, c.id asc",
            countQuery = "select count(c) from SubmissionComment c where c.submission.id = :submissionId")
    Page<SubmissionComment> findWithAuthorBySubmissionId(@Param("submissionId") Long submissionId, Pageable pageable);
}
