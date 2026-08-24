package com.sat.lms.notice.repository;

import com.sat.lms.notice.dto.NoticeListResponse;
import com.sat.lms.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    @Query(value = """
            select new com.sat.lms.notice.dto.NoticeListResponse(
                n.id, n.title, n.pinned, n.createdAt, a.name,
                case when nr.id is null then false else true end)
            from Notice n
            join n.admin a
            left join NoticeRead nr on nr.notice = n and nr.member.id = :memberId
            where (:unreadOnly = false or nr.id is null)
            order by n.pinned desc, n.createdAt desc
            """,
            countQuery = """
            select count(n.id)
            from Notice n
            left join NoticeRead nr on nr.notice = n and nr.member.id = :memberId
            where (:unreadOnly = false or nr.id is null)
            """)
    Page<NoticeListResponse> findNoticePage(@Param("memberId") Long memberId,
                                            @Param("unreadOnly") boolean unreadOnly,
                                            Pageable pageable);

    @Query("""
            select count(n.id) from Notice n
            where not exists (
                select nr.id from NoticeRead nr
                where nr.notice = n and nr.member.id = :memberId)
            """)
    long countUnreadByMemberId(@Param("memberId") Long memberId);

    @EntityGraph(attributePaths = "admin")
    @Query("select n from Notice n where n.id = :id")
    Optional<Notice> findWithAdminById(@Param("id") Long id);
}
