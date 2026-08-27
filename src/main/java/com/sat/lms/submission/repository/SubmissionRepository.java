package com.sat.lms.submission.repository;

import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    @Query(value = """
            select new com.sat.lms.submission.dto.SubmissionListResponse(
                s.id, a.id, a.title, s.textContent, s.late, s.createdAt, s.updatedAt)
            from Submission s
            join s.assignment a
            where s.student.id = :studentId
            order by s.createdAt desc, s.id desc
            """,
            countQuery = """
            select count(s.id) from Submission s where s.student.id = :studentId
            """)
    Page<SubmissionListResponse> findSubmissionPageByStudentId(@Param("studentId") Long studentId,
                                                               Pageable pageable);
    boolean existsByAssignmentId(Long assignmentId);
}
