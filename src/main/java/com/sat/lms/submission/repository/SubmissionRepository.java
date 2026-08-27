package com.sat.lms.submission.repository;

import com.sat.lms.submission.entity.Submission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    @EntityGraph(attributePaths = {"student", "assignment"})
    @Query("select s from Submission s where s.id = :submissionId")
    Optional<Submission> findWithStudentAndAssignmentById(@Param("submissionId") Long submissionId);
}