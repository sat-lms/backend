package com.sat.lms.assignment.repository;

import com.sat.lms.assignment.dto.AssignmentListResponse;
import com.sat.lms.assignment.entity.Assignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    @Query(value = """
            select new com.sat.lms.assignment.dto.AssignmentListResponse(
                a.id, a.title, a.dueAt, a.allowLateSubmission, a.createdAt, a.updatedAt)
            from Assignment a
            """,
            countQuery = "select count(a.id) from Assignment a")
    Page<AssignmentListResponse> findAssignmentPage(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :assignmentId")
    Optional<Assignment> findByIdForUpdate(@Param("assignmentId") Long assignmentId);
}
