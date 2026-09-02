package com.sat.lms.submission.repository;

import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s join fetch s.student join fetch s.assignment where s.assignment.id = :assignmentId and s.student.id = :studentId")
    Optional<Submission> findByAssignmentIdAndStudentIdForUpdate(@Param("assignmentId") Long assignmentId,
                                                                  @Param("studentId") Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"student", "assignment"})
    @Query("select s from Submission s where s.id = :submissionId")
    Optional<Submission> findWithStudentAndAssignmentByIdForUpdate(@Param("submissionId") Long submissionId);

    boolean existsByAssignmentId(Long assignmentId);

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

    @EntityGraph(attributePaths = {"student", "assignment"})
    @Query("select s from Submission s where s.id = :submissionId")
    Optional<Submission> findWithStudentAndAssignmentById(@Param("submissionId") Long submissionId);
}
