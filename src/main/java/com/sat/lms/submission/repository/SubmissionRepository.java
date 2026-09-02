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
                s.id, a.id, a.title, a.dueAt, a.allowLateSubmission, s.textContent,
                coalesce(s.late, false), s.createdAt, s.updatedAt)
            from Assignment a
            left join Submission s on s.assignment = a and s.student.id = :studentId
            where (:includeNotSubmitted = true or s.id is not null)
            order by a.dueAt desc, a.id desc
            """,
            countQuery = """
            select count(a.id) from Assignment a
            left join Submission s on s.assignment = a and s.student.id = :studentId
            where (:includeNotSubmitted = true or s.id is not null)
            """)
    Page<SubmissionListResponse> findMySubmissionPageDueAtDesc(@Param("studentId") Long studentId,
            @Param("includeNotSubmitted") boolean includeNotSubmitted, Pageable pageable);

    @Query(value = """
            select new com.sat.lms.submission.dto.SubmissionListResponse(
                s.id, a.id, a.title, a.dueAt, a.allowLateSubmission, s.textContent,
                coalesce(s.late, false), s.createdAt, s.updatedAt)
            from Assignment a
            left join Submission s on s.assignment = a and s.student.id = :studentId
            where (:includeNotSubmitted = true or s.id is not null)
            order by a.dueAt asc, a.id asc
            """,
            countQuery = """
            select count(a.id) from Assignment a
            left join Submission s on s.assignment = a and s.student.id = :studentId
            where (:includeNotSubmitted = true or s.id is not null)
            """)
    Page<SubmissionListResponse> findMySubmissionPageDueAtAsc(@Param("studentId") Long studentId,
            @Param("includeNotSubmitted") boolean includeNotSubmitted, Pageable pageable);

    @Query(value = """
            select new com.sat.lms.submission.dto.SubmissionListResponse(
                s.id, a.id, a.title, a.dueAt, a.allowLateSubmission, s.textContent,
                coalesce(s.late, false), s.createdAt, s.updatedAt)
            from Assignment a
            left join Submission s on s.assignment = a and s.student.id = :studentId
            where (:includeNotSubmitted = true or s.id is not null)
            order by case when s.updatedAt is null then 1 else 0 end asc, s.updatedAt desc, a.id desc
            """,
            countQuery = """
            select count(a.id) from Assignment a
            left join Submission s on s.assignment = a and s.student.id = :studentId
            where (:includeNotSubmitted = true or s.id is not null)
            """)
    Page<SubmissionListResponse> findMySubmissionPageSubmittedAtDesc(@Param("studentId") Long studentId,
            @Param("includeNotSubmitted") boolean includeNotSubmitted, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "assignment"})
    @Query("select s from Submission s where s.id = :submissionId")
    Optional<Submission> findWithStudentAndAssignmentById(@Param("submissionId") Long submissionId);
}
