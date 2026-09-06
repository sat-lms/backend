package com.sat.lms.notice.repository;

import com.sat.lms.notice.entity.NoticeComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {

    @Query(value = "select c from NoticeComment c join fetch c.author where c.notice.id = :noticeId "
                    + "order by c.createdAt asc, c.id asc",
            countQuery = "select count(c) from NoticeComment c where c.notice.id = :noticeId")
    Page<NoticeComment> findWithAuthorByNoticeId(@Param("noticeId") Long noticeId, Pageable pageable);
}
