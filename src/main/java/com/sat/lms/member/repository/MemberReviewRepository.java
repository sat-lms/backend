package com.sat.lms.member.repository;

import com.sat.lms.member.entity.MemberReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberReviewRepository extends JpaRepository<MemberReview, Long> {
}