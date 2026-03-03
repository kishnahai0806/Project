package com.campuseventhub.event.repository;

import com.campuseventhub.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @Query(value = """
            SELECT date_trunc('week', e.start_time) AS week_start,
                   e.status AS status,
                   count(*) AS total
            FROM events e
            WHERE e.start_time BETWEEN :from AND :to
            GROUP BY date_trunc('week', e.start_time), e.status
            ORDER BY date_trunc('week', e.start_time)
            """, nativeQuery = true)
    List<Object[]> weeklyMetricsRaw(@Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to);
}
