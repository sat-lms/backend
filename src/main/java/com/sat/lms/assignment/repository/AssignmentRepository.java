package com.sat.lms.assignment.repository;

import com.sat.lms.assignment.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
}