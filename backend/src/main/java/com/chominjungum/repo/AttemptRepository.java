package com.chominjungum.repo;

import com.chominjungum.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
    List<Attempt> findBySessionId(UUID sessionId);

    Optional<Attempt> findBySessionIdAndDeviceBindingIdAndItemId(
            UUID sessionId, String deviceBindingId, UUID itemId);

    List<Attempt> findByStudentId(UUID studentId);

    List<Attempt> findBySessionIdIn(List<UUID> sessionIds);

    List<Attempt> findByDeviceBindingId(String deviceBindingId);

    /** 아직 명단에 연결되지 않은 기기 목록(제출 건수와 함께). 교사 콘솔의 매핑 대기열. */
    @Query("""
            select a.deviceBindingId, count(a)
            from Attempt a
            where a.studentId is null
              and a.deviceBindingId is not null
              and a.sessionId in (
                  select s.id from ExamSession s where s.classroomId = :classroomId
              )
            group by a.deviceBindingId
            order by count(a) desc
            """)
    List<Object[]> findUnassignedDevices(@Param("classroomId") UUID classroomId);
}
