package com.sat.lms.member.repository;

import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.submission.dto.AdminAssignmentSubmissionCounts;
import com.sat.lms.submission.dto.AdminSubmissionStudentRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Page<Member> findByStatus(MemberStatus status, Pageable pageable);

    boolean existsByStudentNumber(String studentNumber);

    Optional<Member> findByStudentNumber(String studentNumber);

    @Query(value = """
            select new com.sat.lms.submission.dto.AdminSubmissionStudentRow(
                s.id, m.studentNumber, m.name, s.createdAt, s.late)
            from Member m
            left join Submission s on s.student = m and s.assignment.id = :assignmentId
            where m.role = com.sat.lms.member.entity.MemberRole.STUDENT
              and m.status = com.sat.lms.member.entity.MemberStatus.APPROVED
              and (:status is null
                   or (:status = 'SUBMITTED' and s.id is not null)
                   or (:status = 'NOT_SUBMITTED' and s.id is null)
                   or (:status = 'LATE' and s.late = true))
            order by m.studentNumber asc
            """,
            countQuery = """
            select count(m.id)
            from Member m
            left join Submission s on s.student = m and s.assignment.id = :assignmentId
            where m.role = com.sat.lms.member.entity.MemberRole.STUDENT
              and m.status = com.sat.lms.member.entity.MemberStatus.APPROVED
              and (:status is null
                   or (:status = 'SUBMITTED' and s.id is not null)
                   or (:status = 'NOT_SUBMITTED' and s.id is null)
                   or (:status = 'LATE' and s.late = true))
            """)
    Page<AdminSubmissionStudentRow> findStudentSubmissionStatusPage(@Param("assignmentId") Long assignmentId,
                                                                    @Param("status") String status,
                                                                    Pageable pageable);

    @Query("""
            select new com.sat.lms.submission.dto.AdminAssignmentSubmissionCounts(
                sum(case when exists (
                        select 1 from Submission s
                        where s.student = m and s.assignment.id = :assignmentId and s.late = false)
                    then 1L else 0L end),
                sum(case when exists (
                        select 1 from Submission s
                        where s.student = m and s.assignment.id = :assignmentId and s.late = true)
                    then 1L else 0L end),
                sum(case when not exists (
                        select 1 from Submission s where s.student = m and s.assignment.id = :assignmentId)
                    then 1L else 0L end))
            from Member m
            where m.role = com.sat.lms.member.entity.MemberRole.STUDENT
              and m.status = com.sat.lms.member.entity.MemberStatus.APPROVED
            """)
    AdminAssignmentSubmissionCounts countSubmissionStatusByAssignmentId(@Param("assignmentId") Long assignmentId);
}
