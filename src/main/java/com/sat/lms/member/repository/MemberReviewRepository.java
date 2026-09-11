package com.sat.lms.member.repository;

import com.sat.lms.member.entity.MemberReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberReviewRepository extends JpaRepository<MemberReview, Long> {

    // 회원 한 명당 심사 기록이 여러 건일 수 있다(탈퇴 → 복구 신청 → 재승인 반복, #107 V11).
    // reviewedAt이 같을 가능성을 대비해 id를 2차 정렬 기준으로 둬 순서를 결정적으로 만들고
    // (이슈 #31의 교훈), Pageable로 정확히 1건 이하만 가져온다. 반환 타입이 Page/Slice가
    // 아닌 List라 count 쿼리 없이 content 쿼리 한 건만 실행된다.
    @Query("""
            select r from MemberReview r
            where r.memberId = :memberId
            order by r.reviewedAt desc, r.id desc
            """)
    List<MemberReview> findLatestByMemberId(@Param("memberId") Long memberId, Pageable pageable);
}