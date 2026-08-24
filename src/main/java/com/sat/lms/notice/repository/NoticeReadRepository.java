package com.sat.lms.notice.repository;

import com.sat.lms.notice.entity.NoticeRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface NoticeReadRepository extends JpaRepository<NoticeRead, Long> {
    boolean existsByNoticeIdAndMemberId(Long noticeId, Long memberId);

    @Modifying
    @Query(value = """
            INSERT INTO notice_read (notice_id, member_id, read_at)
            VALUES (:noticeId, :memberId, :readAt)
            ON CONFLICT (notice_id, member_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("noticeId") Long noticeId,
                       @Param("memberId") Long memberId,
                       @Param("readAt") OffsetDateTime readAt);

    long countByNoticeId(Long noticeId);
}
