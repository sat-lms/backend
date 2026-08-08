package com.sat.lms.member.repository;

import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Page<Member> findByStatus(MemberStatus status, Pageable pageable);

    boolean existsByStudentNumber(String studentNumber);

    Optional<Member> findByStudentNumber(String studentNumber);
}
