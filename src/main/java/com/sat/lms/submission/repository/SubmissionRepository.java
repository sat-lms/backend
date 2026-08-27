package com.sat.lms.submission.repository;

import com.sat.lms.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);
    boolean existsByAssignmentId(Long assignmentId);
}
