package com.exam.examserver.repo;
import com.exam.examserver.model.Notification;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
      select n from Notification n
      where n.userId = :uid
        and (n.expiresAt is null or n.expiresAt > :now)
      order by case when n.readAt is null then 0 else 1 end, n.createdAt desc
    """)
    Page<Notification> listForUser(@Param("uid") Long userId,
                                   @Param("now") Instant now,
                                   Pageable pageable);

    @Query("""
       select count(n)
       from Notification n
       where n.userId = :uid
         and n.readAt is null
         and (n.expiresAt is null or n.expiresAt > :now)
    """)
        long countUnreadActive(@Param("uid") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("""
      update Notification n
         set n.readAt = :ts
       where n.userId = :uid and n.id = :id and n.readAt is null
    """)
    int markOneRead(@Param("uid") Long userId, @Param("id") Long id, @Param("ts") Instant ts);

    @Modifying
    @Query("""
      update Notification n
         set n.isRead = true,
             n.readAt = :ts
       where n.userId = :uid
         and n.isRead = false
         and (n.expiresAt is null or n.expiresAt > :now)
    """)
    int markAllRead(@Param("uid") Long userId, @Param("ts") Instant ts, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.id = :id and n.userId = :uid")
    int deleteOne(@Param("uid") Long userId, @Param("id") Long id);

}

